package com.example.shop.order.service.rest.order

import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.javadsl.AskPattern
import akka.http.javadsl.marshallers.jackson.Jackson
import akka.http.javadsl.server.Directives.*
import com.example.shop.order.service.app.service.order.OrderService
import com.example.shop.order.service.rest.order.models.create.OrderCreateRequest
import com.fasterxml.jackson.databind.ObjectMapper


class OrderRoutes(
    private val system: ActorSystem<*>,
    private val objectMapper: ObjectMapper,
    private val orderService: ActorRef<OrderService.Message>
) {
    private val timeout = system.settings().config().getDuration("order-service.ask-timeout")

    fun routes() = orderRoutes()

    private fun orderRoutes() =
        pathPrefix("orders") {
            concat(
                post()
            )
        }

    private fun post() =
        post {
            entity(Jackson.unmarshaller(objectMapper, OrderCreateRequest::class.java)) { request ->
                val createOrder = {
                    AskPattern.ask(
                        orderService,
                        { replyTo: ActorRef<OrderService.CreateOrderReply> ->
                            OrderService.CreateOrder(
                                request.accountId,
                                replyTo
                            )
                        },
                        timeout,
                        system.scheduler()
                    )
                }

                onSuccess(createOrder) {
                    completeOK(it.orderId, Jackson.marshaller())
                }
            }
        }
}