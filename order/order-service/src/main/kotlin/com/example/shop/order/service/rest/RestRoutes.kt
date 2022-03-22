package com.example.shop.order.service.rest

import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.http.javadsl.server.Directives.concat
import com.example.shop.order.service.app.service.order.OrderService
import com.example.shop.order.service.rest.order.OrderRoutes
import com.fasterxml.jackson.databind.ObjectMapper

class RestRoutes(system: ActorSystem<*>, objectMapper: ObjectMapper, orderService: ActorRef<OrderService.Message>) {
    val orderRoutes = OrderRoutes(system, objectMapper, orderService)

    fun routes() = concat(
        orderRoutes.routes()
    )
}