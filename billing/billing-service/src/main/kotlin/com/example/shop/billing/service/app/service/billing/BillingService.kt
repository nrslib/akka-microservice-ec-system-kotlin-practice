package com.example.shop.billing.service.app.service.billing

import akka.Done
import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.javadsl.*
import akka.cluster.sharding.typed.javadsl.ClusterSharding
import akka.cluster.sharding.typed.javadsl.EntityRef
import akka.pattern.StatusReply
import akka.persistence.query.journal.leveldb.javadsl.LeveldbReadJournal
import akka.stream.javadsl.Sink
import com.example.kafka.delivery.KafkaProducer
import com.example.shop.billing.service.app.model.billing.Billing
import com.example.shop.billing.service.app.model.billing.BillingDetail
import com.example.shop.order.api.order.replies.ApproveBillingReply
import com.example.shop.order.api.order.replies.OrderCreateSagaReply
import com.example.shop.shared.id.IdGenerator
import java.util.*

class BillingService(
    context: ActorContext<Message>,
    private val idGenerator: IdGenerator,
    private val readJournal: LeveldbReadJournal,
    private val orderReplyProducerBehaviorProvider: () -> Behavior<KafkaProducer.Message>
) : AbstractBehavior<BillingService.Message>(context) {
    companion object {
        fun create(
            idGenerator: IdGenerator,
            readJournal: LeveldbReadJournal,
            orderReplyProducerBehaviorProvider: () -> Behavior<KafkaProducer.Message>
        ): Behavior<Message> = Behaviors.setup {
            BillingService(it, idGenerator, readJournal, orderReplyProducerBehaviorProvider)
        }
    }

    sealed interface Message
    data class HoldBilling(val orderId: String, val consumerId: String, val replyTo: ActorRef<StatusReply<String>>) :
        Message

    data class GetAllId(val replyTo: ActorRef<List<String>>) : Message
    data class GetDetail(val billingId: String, val replyTo: ActorRef<BillingDetail>) : Message
    data class ApproveOrder(val billingId: String, val replyTo: ActorRef<StatusReply<Done>>) : Message
    data class ReplyOrder(val reply: OrderCreateSagaReply) : Message

    private val clusterSharding = ClusterSharding.get(context.system)
    private val timeout = context.system.settings().config().getDuration("service.ask-timeout")

    override fun createReceive(): Receive<Message> =
        newReceiveBuilder()
            .onMessage(HoldBilling::class.java) { message ->
                val billingId = idGenerator.generate()
                val billing = getBilling(billingId)
                billing.tell(Billing.OpenBilling(message.consumerId, message.orderId))

                message.replyTo.tell(StatusReply.success(billingId))

                this
            }
            .onMessage(GetAllId::class.java) { message ->
                val ids = readJournal.currentPersistenceIds()
                val future = ids.runWith(Sink.seq(), context.system)
                future.thenApply {
                    message.replyTo.tell(it)
                }

                this
            }
            .onMessage(GetDetail::class.java) { message ->
                val billing = getBilling(message.billingId)
                AskPattern.ask(
                    billing,
                    { replyTo: ActorRef<BillingDetail> -> Billing.GetDetail(replyTo) },
                    timeout,
                    context.system.scheduler()
                ).thenApply {
                    message.replyTo.tell(it)
                }

                this
            }
            .onMessage(ApproveOrder::class.java) { message ->
                val billing = getBilling(message.billingId)

                AskPattern.ask(
                    billing,
                    { replyTo: ActorRef<StatusReply<String>> -> Billing.Approve(replyTo) },
                    timeout,
                    context.system.scheduler()
                ).thenApply {
                    message.replyTo.tell(StatusReply.Ack())

                    val orderId = it.value
                    val reply = ReplyOrder(ApproveBillingReply(orderId, true, message.billingId))
                    context.self.tell(reply)
                }

                this
            }
            .onMessage(ReplyOrder::class.java) {
                val producer =
                    context.spawn(orderReplyProducerBehaviorProvider(), "orderReplyProducer-${UUID.randomUUID()}")
                producer.tell(KafkaProducer.Send(it.reply.orderId, it.reply))

                this
            }
            .build()

    private fun getBilling(billingId: String): EntityRef<Billing.Command> {
        return clusterSharding.entityRefFor(Billing.typekey(), billingId)
    }
}