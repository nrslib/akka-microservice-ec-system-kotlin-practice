package com.example.shop.stock.service.handlers

import akka.Done
import akka.actor.typed.ActorRef
import akka.actor.typed.javadsl.*
import akka.pattern.StatusReply
import com.example.kafka.delivery.KafkaConfig
import com.example.kafka.delivery.KafkaProducer
import com.example.shop.order.api.order.OrderServiceChannels
import com.example.shop.order.api.order.replies.CancelSecureReply
import com.example.shop.order.api.order.replies.OrderCreateSagaReply
import com.example.shop.order.api.order.replies.SecureInventoryReply
import com.example.shop.stock.api.stock.commands.CancelSecure
import com.example.shop.stock.api.stock.commands.SecureInventory
import com.example.shop.stock.api.stock.commands.StockServiceCommand
import com.example.shop.stock.service.app.service.stock.StockService
import java.time.Duration
import java.util.*

class StockServiceCommandHandler(context: ActorContext<Message>, private val kafkaConfig: KafkaConfig) :
    AbstractBehavior<StockServiceCommandHandler.Message>(context) {
    companion object {
        fun create(kafkaConfig: KafkaConfig) = Behaviors.setup<Message> {
            StockServiceCommandHandler(it, kafkaConfig)
        }
    }

    sealed interface Message
    data class Handle(val message: StockServiceCommand) : Message
    data class ReplyOrderCreatedSaga(val reply: OrderCreateSagaReply) : Message

    override fun createReceive(): Receive<Message> =
        newReceiveBuilder()
            .onMessage(Handle::class.java) {
                val id = UUID.randomUUID().toString()
                val service = context.spawn(StockService.create(), "stockService-$id")

                when (it.message) {
                    is SecureInventory -> {
                        val future = AskPattern.ask(
                            service,
                            { replyTo: ActorRef<StatusReply<Done>> ->
                                StockService.SecureInventory(
                                    it.message.orderId,
                                    it.message.itemId,
                                    replyTo
                                )
                            },
                            Duration.ofSeconds(5),
                            context.system.scheduler()
                        )

                        future.whenComplete { status, ex ->
                            if (status != null) {
                                val message = SecureInventoryReply(it.message.orderId, status.isSuccess)
                                context.self.tell(ReplyOrderCreatedSaga(message))
                            } else {
                                throw ex
                            }
                        }
                    }
                    is CancelSecure -> {
                        val future = AskPattern.ask(
                            service,
                            { replyTo: ActorRef<StatusReply<Done>> ->
                                StockService.CancelSecure(
                                    it.message.orderId,
                                    replyTo
                                )
                            },
                            Duration.ofSeconds(5),
                            context.system.scheduler()
                        )

                        future.whenComplete { status, ex ->
                            if (status != null) {
                                val message = CancelSecureReply(it.message.orderId, status.isSuccess)
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
            "createOrderSagaReplyProducer"
        )
}