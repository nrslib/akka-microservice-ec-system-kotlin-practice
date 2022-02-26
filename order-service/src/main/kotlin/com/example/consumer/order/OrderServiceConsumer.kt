package com.example.consumer.order

import akka.actor.typed.ActorRef
import akka.actor.typed.delivery.ConsumerController
import akka.actor.typed.javadsl.AbstractBehavior
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.Behaviors
import akka.actor.typed.javadsl.Receive
import com.example.app.provider.OrderServiceProvider
import com.example.app.service.order.OrderService

class OrderServiceConsumer(
    context: ActorContext<Message>,
    private val orderServiceProvider: OrderServiceProvider,
    private val entityId: String,
    private val consumerController: ActorRef<ConsumerController.Start<Message>>
) : AbstractBehavior<OrderServiceConsumer.Message>(context) {
    companion object {
        fun create(orderServiceProvider: OrderServiceProvider, entityId: String, consumerController: ActorRef<ConsumerController.Start<Message>>) =
            Behaviors.setup<Message> {
                it.self.tell(Initialize)

                OrderServiceConsumer(it, orderServiceProvider, entityId, consumerController)
            }
    }

    sealed interface Message
    object Initialize : Message
    data class CommandDelivery(val message: Message, val confirmTo: ActorRef<ConsumerController.Confirmed>) : Message
    data class Approved(val orderId: String, val replyTo: ActorRef<OrderService.ApproveOrderResponse>) : Message

    override fun createReceive(): Receive<Message> =
        newReceiveBuilder()
            .onMessage(Initialize::class.java) {
                val deliveryAdapter =
                    context.messageAdapter<ConsumerController.Delivery<Message>>(ConsumerController.deliveryClass()) {
                        CommandDelivery(it.message(), it.confirmTo())
                    }
                consumerController.tell(ConsumerController.Start(deliveryAdapter))

                this
            }
            .onMessage(Approved::class.java) { (orderId, replyTo) ->
                val orderService =
                    context.spawn(orderServiceProvider.provide(), "orderService-fromOrderServiceConsumer-$entityId")
                orderService.tell(OrderService.ApproveOrder(orderId, replyTo))

                Behaviors.stopped()
            }
            .onMessage(CommandDelivery::class.java) {
                context.self.tell(it.message)
                it.confirmTo.tell(ConsumerController.confirmed())

                this
            }
            .build()
}