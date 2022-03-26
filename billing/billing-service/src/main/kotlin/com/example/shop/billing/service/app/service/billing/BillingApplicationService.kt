package com.example.shop.billing.service.app.service.billing

import akka.actor.typed.ActorRef
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.AskPattern
import akka.cluster.sharding.typed.javadsl.ClusterSharding
import akka.cluster.sharding.typed.javadsl.EntityRef
import akka.pattern.StatusReply
import akka.persistence.query.journal.leveldb.javadsl.LeveldbReadJournal
import akka.stream.javadsl.Sink
import com.example.kafka.delivery.KafkaProducers
import com.example.shop.billing.service.app.model.billing.Billing
import com.example.shop.billing.service.app.model.billing.BillingDetail
import com.example.shop.order.api.order.OrderServiceChannels
import com.example.shop.order.api.order.replies.ApproveBillingReply
import com.example.shop.shared.id.IdGenerator
import java.time.Duration
import java.util.concurrent.CompletionStage

class BillingApplicationService(
    private val context: ActorContext<*>,
    private val idGenerator: IdGenerator,
    private val readJournal: LeveldbReadJournal,
    private val kafkaProducers: ActorRef<KafkaProducers.Message>
) {
    private val clusterSharding: ClusterSharding = ClusterSharding.get(context.system)
    private val timeout: Duration = context.system.settings().config().getDuration("service.ask-timeout")

    fun holdBilling(consumerId: String, orderId: String): String {
        val billingId = idGenerator.generate()
        val billing = getBilling(billingId)
        billing.tell(Billing.OpenBilling(consumerId, orderId))

        return billingId
    }

    fun getAll(): CompletionStage<List<String>> {
        val idsSource = readJournal.currentPersistenceIds()

        return idsSource.runWith(Sink.seq(), context.system)
    }

    fun get(billingId: String): CompletionStage<BillingDetail> {
        val billing = getBilling(billingId)

        return AskPattern.ask(
            billing,
            { replyTo: ActorRef<BillingDetail> -> Billing.GetDetail(replyTo) },
            timeout,
            context.system.scheduler()
        )
    }

    fun approve(billingId: String): CompletionStage<StatusReply<String>> {
        val billing = getBilling(billingId)

        return AskPattern.ask(
            billing,
            { replyTo: ActorRef<StatusReply<String>> -> Billing.Approve(replyTo) },
            timeout,
            context.system.scheduler()
        ).thenApply { result ->
            val orderId = result.value
            val reply = ApproveBillingReply(orderId, true, billingId)
            kafkaProducers.tell(KafkaProducers.Send(OrderServiceChannels.createOrderSagaReplyChannel, orderId, reply))

            result
        }
    }

    private fun getBilling(billingId: String): EntityRef<Billing.Command> {
        return clusterSharding.entityRefFor(Billing.typekey(), billingId)
    }
}