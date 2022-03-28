package com.example.shop.stock.service.handlers

import akka.actor.typed.javadsl.AbstractBehavior
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.Behaviors
import akka.actor.typed.javadsl.Receive
import com.example.kafka.delivery.KafkaConfig
import com.example.kafka.delivery.KafkaProducer
import com.example.shop.order.api.order.OrderServiceChannels
import com.example.shop.order.api.order.replies.CancelSecureReply
import com.example.shop.order.api.order.replies.OrderCreateSagaReply
import com.example.shop.order.api.order.replies.SecureInventorySucceeded
import com.example.shop.stock.api.stock.commands.CancelSecure
import com.example.shop.stock.api.stock.commands.SecureInventory
import com.example.shop.stock.api.stock.commands.StockServiceCommand
import com.example.shop.stock.service.app.service.stock.StockApplicationService
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
                val service = StockApplicationService(context)
                when (it.message) {
                    is SecureInventory -> {
                        service.secureInventory(it.message.orderId, "")
                        val message = SecureInventorySucceeded(it.message.orderId)
                        context.self.tell(ReplyOrderCreatedSaga(message))
                    }
                    is CancelSecure -> {
                        service.cancelSecure(it.message.orderId)
                        val message = CancelSecureReply(it.message.orderId, true)
                        context.self.tell(ReplyOrderCreatedSaga(message))
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