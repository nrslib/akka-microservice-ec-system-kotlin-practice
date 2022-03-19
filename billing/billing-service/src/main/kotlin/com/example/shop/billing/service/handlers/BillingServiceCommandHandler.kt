package com.example.shop.billing.service.handlers

import akka.Done
import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.javadsl.*
import akka.pattern.StatusReply
import com.example.kafka.delivery.KafkaConfig
import com.example.kafka.delivery.KafkaProducer
import com.example.shop.billing.api.billing.commands.ApproveOrder
import com.example.shop.billing.api.billing.commands.ApproveOrderReply
import com.example.shop.billing.api.billing.commands.BillingServiceCommand
import com.example.shop.billing.api.billing.commands.BillingServiceReply
import com.example.shop.billing.service.app.service.billing.BillingService
import com.example.shop.order.api.order.OrderServiceChannels
import com.example.shop.shared.id.UuidIdGenerator
import java.time.Duration
import java.util.*

class BillingServiceCommandHandler(context: ActorContext<Message>, private val kafkaConfig: KafkaConfig) : AbstractBehavior<BillingServiceCommandHandler.Message>(context) {
    companion object {
        fun create(kafkaConfig: KafkaConfig) = Behaviors.setup<Message> {
            BillingServiceCommandHandler(it, kafkaConfig)
        }
    }

    sealed interface Message
    data class Handle(val message: BillingServiceCommand) : Message

    override fun createReceive(): Receive<Message> =
        newReceiveBuilder()
            .onMessage(Handle::class.java) {
                val id = UUID.randomUUID().toString()
                val service = context.spawn(BillingService.create(UuidIdGenerator()), "billingService-$id")

                when (it.message) {
                    is ApproveOrder -> {
                        val future = AskPattern.ask(
                            service,
                            { replyTo: ActorRef<StatusReply<Done>> ->
                                BillingService.ApproveOrder(
                                    it.message.orderId,
                                    replyTo
                                )
                            },
                            Duration.ofSeconds(5),
                            context.system.scheduler()
                        )

                        future.whenComplete { status, ex ->
                            if (status != null) {
                                val message = ApproveOrderReply(it.message.orderId, status.isSuccess)
                                tellToReplyProducer(it.message.orderId, message)
                            } else {
                                throw ex
                            }
                        }
                    }
                }

                this
            }
            .build()

    private fun tellToReplyProducer(orderId: String, reply: BillingServiceReply) {
        val producer = spawnReplyProducer()
        producer.tell(KafkaProducer.Send(orderId, reply))
    }

    private fun spawnReplyProducer() =
        context.spawn(
            KafkaProducer.create(OrderServiceChannels.createOrderSagaReplyChannel, kafkaConfig),
            "billingServiceProducer"
        )
}