package com.example.shop.billing.service.handlers

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.javadsl.*
import akka.pattern.StatusReply
import akka.persistence.query.journal.leveldb.javadsl.LeveldbReadJournal
import com.example.kafka.delivery.KafkaConfig
import com.example.kafka.delivery.KafkaProducer
import com.example.shop.billing.api.billing.commands.ApproveOrder
import com.example.shop.billing.api.billing.commands.BillingServiceCommand
import com.example.shop.billing.service.app.service.billing.BillingService
import com.example.shop.order.api.order.OrderServiceChannels
import com.example.shop.order.api.order.replies.ApproveBillingReply
import com.example.shop.order.api.order.replies.OrderCreateSagaReply
import com.example.shop.shared.id.UuidIdGenerator
import java.time.Duration
import java.util.*

class BillingServiceCommandHandler(
    context: ActorContext<Message>,
    private val kafkaConfig: KafkaConfig,
    private val orderReplyProducerBehaviorProvider: () -> Behavior<KafkaProducer.Message>,
    private val readJournal: LeveldbReadJournal
) :
    AbstractBehavior<BillingServiceCommandHandler.Message>(context) {
    companion object {
        fun create(
            kafkaConfig: KafkaConfig,
            orderReplyProducerBehaviorProvider: () -> Behavior<KafkaProducer.Message>,
            readJournal: LeveldbReadJournal
        ) = Behaviors.setup<Message> {
            BillingServiceCommandHandler(it, kafkaConfig, orderReplyProducerBehaviorProvider, readJournal)
        }
    }

    sealed interface Message
    data class Handle(val message: BillingServiceCommand) : Message
    data class ReplyOrderCreatedSaga(val reply: OrderCreateSagaReply) : Message

    override fun createReceive(): Receive<Message> =
        newReceiveBuilder()
            .onMessage(Handle::class.java) {
                val id = UUID.randomUUID().toString()
                val service = context.spawn(
                    BillingService.create(
                        UuidIdGenerator(),
                        readJournal,
                        orderReplyProducerBehaviorProvider
                    ), "billingService-$id"
                )

                when (it.message) {
                    is ApproveOrder -> {
                        val future = AskPattern.ask(
                            service,
                            { replyTo: ActorRef<StatusReply<String>> ->
                                BillingService.HoldBilling(
                                    it.message.orderId,
                                    it.message.consumerId,
                                    replyTo
                                )
                            },
                            Duration.ofSeconds(5),
                            context.system.scheduler()
                        )

                        future.whenComplete { statusReply, ex ->
                            if (statusReply != null) {
                                val message =
                                    ApproveBillingReply(it.message.orderId, statusReply.isSuccess, statusReply.value)
                                context.self.tell(ReplyOrderCreatedSaga(message))
                            } else {
                                throw ex
                            }
                        }
                    }
                }

                this
            }
            .onMessage(ReplyOrderCreatedSaga::class.java) {
                tellToReplyProducer(it.reply.orderId, it.reply)

                this
            }
            .build()

    private fun tellToReplyProducer(orderId: String, reply: OrderCreateSagaReply) {
        val producer = spawnReplyProducer()
        producer.tell(KafkaProducer.Send(orderId, reply))
    }

    private fun spawnReplyProducer() =
        context.spawn(
            KafkaProducer.create(OrderServiceChannels.createOrderSagaReplyChannel, kafkaConfig),
            "billingServiceProducer"
        )
}