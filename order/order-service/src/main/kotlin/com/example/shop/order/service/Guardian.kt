package com.example.shop.order.service

import akka.actor.typed.Behavior
import akka.actor.typed.javadsl.Behaviors
import akka.cluster.typed.Cluster
import com.example.shop.order.service.app.model.order.Order
import com.example.shop.order.service.app.provider.OrderServiceProvider
import com.example.shop.order.service.app.service.order.OrderService
import com.example.shop.order.service.saga.order.create.OrderCreateSaga
import com.example.shop.shared.id.FixedIdGenerator

object Guardian {
    fun create(): Behavior<Void> = Behaviors.setup {
        val selfAddress = Cluster.get(it.system).selfMember().address()
        val hostAndPort = "${selfAddress.host.get()}:${selfAddress.port.get()}"
        val actorNameSuffix = "-fromGuardian-$hostAndPort"

        val orderServiceProvider = OrderServiceProvider(FixedIdGenerator("test-order-id"))

        OrderCreateSaga.initSharding(it)

        Order.initSharding(it.system)

        val orderService = it.spawn(orderServiceProvider.provide(), "orderService$actorNameSuffix")
        val printer = it.spawn(
            Behaviors.receive(OrderService.CreateOrderReply::class.java)
                .onMessage(OrderService.CreateOrderReply::class.java) { reply ->
                    println(reply)
                    Behaviors.same()
                }.build(), "printer$actorNameSuffix"
        )

        orderService.tell(OrderService.CreateOrder("test", printer))

        Behaviors.empty()
    }
}