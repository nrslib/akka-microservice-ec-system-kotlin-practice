package com.example.app.service.order

import akka.Done
import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.javadsl.*
import akka.cluster.sharding.typed.javadsl.ClusterSharding
import akka.cluster.sharding.typed.javadsl.Entity
import akka.cluster.sharding.typed.javadsl.EntityRef
import akka.cluster.sharding.typed.javadsl.EntityTypeKey
import akka.pattern.StatusReply
import com.example.app.model.order.Order
import com.example.app.model.order.OrderState
import com.example.saga.order.create.OrderCreateSaga
import com.example.shared.id.IdGenerator

class OrderService(
    context: ActorContext<Message>,
    private val orderIdGenerator: IdGenerator
) : AbstractBehavior<OrderService.Message>(context) {
    companion object {
        fun create(idGenerator: IdGenerator): Behavior<Message> = Behaviors.setup {
            OrderService(it, idGenerator)
        }
    }

    sealed interface Message
    data class GetOrder(val orderId: String, val replyTo: ActorRef<OrderState>) : Message
    data class CreateOrder(val accountId: String, val replyTo: ActorRef<CreateOrderReply>) : Message
    data class CreateOrderReply(val orderId: String) : Message
    data class CancelOrder(val orderId: String, val replyTo: ActorRef<Message>) : Message
    data class CancelOrderReply(val success: Boolean) : Message
    data class ApproveOrder(val orderId: String, val replyTo: ActorRef<ApproveOrderResponse>) : Message
    data class ApproveOrderResponse(val success: Boolean)

    private val clusterSharding = ClusterSharding.get(context.system)
    private val timeout = context.system.settings().config().getDuration("order-service.ask-timeout")

    override fun createReceive(): Receive<Message> =
        newReceiveBuilder()
            .onMessage(GetOrder::class.java) { (orderId, replyTo) ->
                val order = getOrder(orderId)
                val future = AskPattern.ask(
                    order,
                    {replyTo: ActorRef<OrderState> -> Order.Get(replyTo)},
                    timeout,
                    context.system.scheduler()
                )

                future.toCompletableFuture()
                    .thenApply {
                        replyTo.tell(it)
                    }

                this
            }
            .onMessage(CreateOrder::class.java) { (_, replyTo) ->
                val orderId = orderIdGenerator.generate()
                val order = getOrder(orderId)
                order.tell(Order.Create)

                replyTo.tell(CreateOrderReply(orderId))

                val saga = getOrderCreateSaga(orderId + "saga")
                saga.tell(OrderCreateSaga.StartSaga(orderId))

                this
            }
            .onMessage(CancelOrder::class.java) { (orderId, replyTo) ->
                val order = getOrder(orderId)
                val futureStage = AskPattern.ask(
                    order,
                    { replyTo: ActorRef<Message> -> Order.Cancel(replyTo) },
                    timeout,
                    context.system.scheduler()
                )
                val future = futureStage.toCompletableFuture()
                future.thenApply {
                    replyTo.tell(it)
                }

                this
            }
            .onMessage(ApproveOrder::class.java) { (orderId, replyTo) ->
                val order = getOrder(orderId)
                val result = AskPattern.askWithStatus(
                    order,
                    {replyTo: ActorRef<StatusReply<Done>> -> Order.Approve(replyTo)},
                    timeout,
                    context.system.scheduler()
                )

                result.toCompletableFuture()
                    .thenApply {
                        replyTo.tell(ApproveOrderResponse(it is Done))
                    }

                this
            }
            .build()

    private fun getOrder(orderId: String): EntityRef<Order.Command> {
        return ClusterSharding.get(context.system).entityRefFor(Order.typekey(), orderId)
    }

    private fun getOrderCreateSaga(orderId: String): EntityRef<OrderCreateSaga.Message> {
        return clusterSharding.entityRefFor(OrderCreateSaga.typekey(), "orderCreateSaga-$orderId")
    }
}