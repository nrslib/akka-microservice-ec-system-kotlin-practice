package com.example.shop.billing.service.handlers

import akka.actor.typed.javadsl.AbstractBehavior
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.Behaviors
import akka.actor.typed.javadsl.Receive
import akka.persistence.query.journal.leveldb.javadsl.LeveldbReadJournal
import com.example.kafka.delivery.KafkaConfig
import com.example.kafka.delivery.KafkaProducer
import com.example.kafka.delivery.KafkaProducers
import com.example.shop.billing.api.billing.commands.ApproveOrder
import com.example.shop.billing.api.billing.commands.BillingServiceCommand
import com.example.shop.billing.service.app.service.billing.BillingApplicationService
import com.example.shop.order.api.order.OrderServiceChannels
import com.example.shop.order.api.order.replies.ApproveBillingReply
import com.example.shop.order.api.order.replies.ApproveBillingReplySucceeded
import com.example.shop.order.api.order.replies.OrderCreateSagaReply
import com.example.shop.shared.id.UuidIdGenerator
import java.util.*

class BillingServiceCommandHandler(
    context: ActorContext<Message>,
    private val kafkaConfig: KafkaConfig,
    private val readJournal: LeveldbReadJournal
) :
    AbstractBehavior<BillingServiceCommandHandler.Message>(context) {
    companion object {
        fun create(
            kafkaConfig: KafkaConfig,
            readJournal: LeveldbReadJournal
        ) = Behaviors.setup<Message> {
            BillingServiceCommandHandler(it, kafkaConfig, readJournal)
        }
    }

    sealed interface Message
    data class Handle(val message: BillingServiceCommand) : Message
    data class ReplyOrderCreatedSaga(val reply: OrderCreateSagaReply) : Message

    override fun createReceive(): Receive<Message> =
        newReceiveBuilder()
            .onMessage(Handle::class.java) {
                val kafkaProducers =
                    context.spawn(KafkaProducers.create(kafkaConfig), "kafkaProducers-${UUID.randomUUID()}")
                val service = BillingApplicationService(context, UuidIdGenerator(), readJournal, kafkaProducers)

                when (it.message) {
                    is ApproveOrder -> {
                        val billingId = service.holdBilling(it.message.consumerId, it.message.orderId)
                        val reply = ApproveBillingReplySucceeded(it.message.orderId, billingId)
                        kafkaProducers.tell(
                            KafkaProducers.Send(
                                OrderServiceChannels.createOrderSagaReplyChannel,
                                it.message.orderId,
                                reply
                            )
                        )
                    }
                }

                Behaviors.stopped()
            }
            .onMessage(ReplyOrderCreatedSaga::class.java) {
                tellToReplyProducer(it.reply.orderId, it.reply)

                Behaviors.stopped()
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