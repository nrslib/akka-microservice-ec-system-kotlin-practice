package com.example.shop.billing.service.rest

import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.http.javadsl.server.Directives
import com.example.shop.billing.service.app.service.billing.BillingService
import com.example.shop.billing.service.rest.billing.BillingRoutes
import com.fasterxml.jackson.databind.ObjectMapper

class RestRoutes(system: ActorSystem<*>, objectMapper: ObjectMapper, orderService: ActorRef<BillingService.Message>) {
    val billingRoute = BillingRoutes(system, objectMapper, orderService)

    fun routes() = Directives.concat(
        billingRoute.routes()
    )
}