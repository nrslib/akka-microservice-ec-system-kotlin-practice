package com.example.shop.order.service.consumer.order

import akka.Done
import akka.actor.typed.ActorRef
import akka.actor.typed.delivery.ConsumerController
import akka.actor.typed.javadsl.*
import akka.pattern.StatusReply
import com.example.shop.order.service.app.provider.OrderServiceProvider
import com.example.shop.order.service.app.service.order.OrderService
import com.example.shop.order.service.saga.order.create.OrderCreateSaga

class OrderServiceConsumer(
    context: ActorContext<Message>,
    private val orderServiceProvider: OrderServiceProvider,
    private val entityId: String,
    private val consumerController: ActorRef<ConsumerController.Start<Message>>
) : AbstractBehavior<OrderServiceConsumer.Message>(context) {
    companion object {
        fun create(
            orderServiceProvider: OrderServiceProvider,
            entityId: String,
            consumerController: ActorRef<ConsumerController.Start<Message>>
        ) =
            Behaviors.setup<Message> {
                it.self.tell(Initialize)

                OrderServiceConsumer(it, orderServiceProvider, entityId, consumerController)
            }
    }

    sealed interface Message
    object Initialize : Message
    data class CommandDelivery(val message: Message, val confirmTo: ActorRef<ConsumerController.Confirmed>) : Message
    data class Approved(val orderId: String, val replyTo: ActorRef<OrderCreateSaga.Message>) : Message

    val timeout = context.system.settings().config().getDuration("order-service.ask-timeout")

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
            .onMessage(CommandDelivery::class.java) {
                context.self.tell(it.message)
                it.confirmTo.tell(ConsumerController.confirmed())

                this
            }
            .onMessage(Approved::class.java) { (orderId, replyTo) ->
                val orderService =
                    context.spawn(orderServiceProvider.provide(), "orderService-fromOrderServiceConsumer-$entityId")
                val futureStage = AskPattern.askWithStatus(
                    orderService,
                    { replyTo: ActorRef<StatusReply<Done>> -> OrderService.ApproveOrder(orderId, replyTo) },
                    timeout,
                    context.system.scheduler()
                )
                futureStage.toCompletableFuture()
                    .thenApply {
                        replyTo.tell(OrderCreateSaga.ApproveReply(true))
                    }
                    .exceptionally {
                        replyTo.tell(OrderCreateSaga.ApproveReply(false))
                    }

                Behaviors.stopped()
            }
            .build()
}