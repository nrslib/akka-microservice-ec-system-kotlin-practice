package com.example.shop.order.service.rest

import akka.actor.typed.javadsl.ActorContext
import akka.http.javadsl.server.Directives.concat
import com.example.shop.order.service.rest.order.OrderRoutes
import com.fasterxml.jackson.databind.ObjectMapper

class RestRoutes(context: ActorContext<*>, objectMapper: ObjectMapper) {
    val orderRoutes = OrderRoutes(context, objectMapper)

    fun routes() = concat(
        orderRoutes.routes()
    )
}