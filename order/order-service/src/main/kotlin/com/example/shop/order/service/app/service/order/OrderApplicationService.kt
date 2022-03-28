package com.example.shop.order.service.app.service.order

import akka.actor.typed.ActorRef
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.AskPattern
import akka.cluster.sharding.typed.javadsl.ClusterSharding
import akka.cluster.sharding.typed.javadsl.EntityRef
import com.example.shop.order.api.order.models.OrderDetail
import com.example.shop.order.service.app.model.order.Order
import com.example.shop.order.service.app.model.order.OrderState
import com.example.shop.order.service.saga.order.create.OrderCreateSaga
import com.example.shop.shared.id.IdGenerator
import java.time.Duration
import java.util.concurrent.CompletableFuture


class OrderApplicationService(private val context: ActorContext<*>, private val idGenerator: IdGenerator) {
    private val clusterSharding: ClusterSharding = ClusterSharding.get(context.system)
    private val timeout: Duration = context.system.settings().config().getDuration("service.ask-timeout")

    fun createOrder(consumerId: String): CompletableFuture<OrderCreateResult> {
        val orderId = idGenerator.generate()
        val detail = OrderDetail(consumerId)
        val order = entityRefForOrder(orderId)
        val stage = AskPattern.ask(
            order,
            { replyTo: ActorRef<Order.CreateOrderReply> -> Order.CreateOrder(replyTo) },
            timeout,
            context.system.scheduler()
        )
            .toCompletableFuture()
            .thenApply {
                val saga = entityRefForOrderCreateSaga(orderId)
                saga.tell(OrderCreateSaga.StartSaga(orderId, detail))

                it
            }

        return stage.thenApply { OrderCreateResult(orderId, it is Order.CreateOrderSucceeded) }
    }

    fun get(orderId: String): CompletableFuture<OrderState> {
        val order = entityRefForOrder(orderId)

        return AskPattern.ask(
            order,
            { replyTo: ActorRef<OrderState> -> Order.Get(replyTo) },
            timeout,
            context.system.scheduler()
        ).toCompletableFuture()
    }

    private fun entityRefForOrder(orderId: String): EntityRef<Order.Command> {
        return clusterSharding.entityRefFor(Order.typekey(), orderId)
    }

    private fun entityRefForOrderCreateSaga(orderId: String): EntityRef<OrderCreateSaga.Message> {
        return clusterSharding.entityRefFor(OrderCreateSaga.typekey(), "orderCreateSaga-$orderId")
    }
}