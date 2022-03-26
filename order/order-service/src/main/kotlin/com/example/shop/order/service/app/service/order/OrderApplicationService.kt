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
import java.util.concurrent.CompletionStage

class OrderApplicationService(private val context: ActorContext<*>, private val idGenerator: IdGenerator) {
    private val clusterSharding: ClusterSharding = ClusterSharding.get(context.system)
    private val timeout: Duration = context.system.settings().config().getDuration("service.ask-timeout")

    fun createOrder(consumerId: String): String {
        val orderId = idGenerator.generate()
        val detail = OrderDetail(consumerId)
        val order = getOrder(orderId)
        order.tell(Order.CreateOrder)

        val saga = getSaga(orderId)
        saga.tell(OrderCreateSaga.StartSaga(orderId, detail))

        return orderId
    }

    fun get(orderId: String): CompletionStage<OrderState> {
        val order = getOrder(orderId)
        val future = AskPattern.ask(
            order,
            { replyTo: ActorRef<OrderState> -> Order.Get(replyTo) },
            timeout,
            context.system.scheduler()
        )

        return future.toCompletableFuture()
    }

    private fun getOrder(orderId: String): EntityRef<Order.Command> {
        return clusterSharding.entityRefFor(Order.typekey(), orderId)
    }

    private fun getSaga(orderId: String): EntityRef<OrderCreateSaga.Message> {
        return clusterSharding.entityRefFor(OrderCreateSaga.typekey(), "orderCreateSaga-$orderId")
    }
}