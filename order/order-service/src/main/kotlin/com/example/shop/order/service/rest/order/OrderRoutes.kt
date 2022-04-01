package com.example.shop.order.service.rest.order

import akka.actor.typed.javadsl.ActorContext
import akka.http.javadsl.marshallers.jackson.Jackson
import akka.http.javadsl.model.StatusCodes
import akka.http.javadsl.server.Directives.*
import akka.http.javadsl.server.PathMatchers.segment
import com.example.shop.order.service.app.service.order.OrderApplicationService
import com.example.shop.order.service.rest.order.models.post.OrderPostRequest
import com.example.shop.shared.id.UuidIdGenerator
import com.fasterxml.jackson.databind.ObjectMapper


class OrderRoutes(
    private val context: ActorContext<*>,
    private val objectMapper: ObjectMapper,
) {
    fun routes() = orderRoutes()

    private fun orderRoutes() =
        pathPrefix("orders") {
            concat(
                get(),
                post()
            )
        }

    private fun post() =
        post {
            entity(Jackson.unmarshaller(objectMapper, OrderPostRequest::class.java)) { request ->
                val service = generateOrderService()
                val orderId = service.createOrder(request.accountId)

                onSuccess(orderId) {
                    if (it.success) {
                        completeOK(it.orderId, Jackson.marshaller())
                    } else {
                        complete(StatusCodes.INTERNAL_SERVER_ERROR)
                    }
                }
            }
        }

    private fun get() =
        get {
            path(segment()) { orderId ->
                val service = generateOrderService()
                val future = service.get(orderId)

                onSuccess(future) { order ->
                    completeOK(order, Jackson.marshaller())
                }
            }
        }

    private fun generateOrderService(): OrderApplicationService {
        return OrderApplicationService(context, UuidIdGenerator())
    }
}