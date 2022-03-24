package com.example.shop.billing.service.rest.billing

import akka.Done
import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.javadsl.AskPattern
import akka.http.javadsl.marshallers.jackson.Jackson
import akka.http.javadsl.model.StatusCodes
import akka.http.javadsl.server.Directives.*
import akka.http.javadsl.server.PathMatchers.segment
import akka.pattern.StatusReply
import com.example.shop.billing.service.app.model.billing.BillingDetail
import com.example.shop.billing.service.app.service.billing.BillingService
import com.example.shop.billing.service.rest.billing.get.BillingGetResponse
import com.fasterxml.jackson.databind.ObjectMapper


class BillingRoutes(
    private val system: ActorSystem<*>,
    private val objectMapper: ObjectMapper,
    private val billingService: ActorRef<BillingService.Message>
) {
    private val timeout = system.settings().config().getDuration("service.ask-timeout")

    fun routes() = billingRoutes()

    private fun billingRoutes() =
        pathPrefix("billings") {
            concat(
                get(),
                getAll(),
                postApprove()
            )
        }

    private fun get() =
        get {
            path(segment()) { billingId ->
                val future = AskPattern.ask(
                    billingService,
                    { replyTo: ActorRef<BillingDetail> ->
                        BillingService.GetDetail(billingId, replyTo)
                    },
                    timeout,
                    system.scheduler()
                )

                onSuccess(future) {
                    val response = BillingGetResponse(it)
                    completeOK(response, Jackson.marshaller())
                }
            }
        }

    private fun getAll() =
        get {
            val future = AskPattern.ask(
                billingService,
                { replyTo: ActorRef<List<String>> ->
                    BillingService.GetAllId(replyTo)
                },
                timeout,
                system.scheduler()
            )

            onSuccess(future) {
                completeOK(it, Jackson.marshaller())
            }
        }

    private fun postApprove() =
        post {
            path(segment().slash("approve")) { billingId ->
                val future = AskPattern.ask(
                    billingService,
                    { replyTo: ActorRef<StatusReply<Done>> ->
                        BillingService.ApproveOrder(billingId, replyTo)
                    },
                    timeout,
                    system.scheduler()
                )

                onSuccess(future) {
                    if (it.isSuccess) {
                        completeOK(billingId, Jackson.marshaller())
                    } else {
                        complete(StatusCodes.BAD_REQUEST, billingId)
                    }
                }
            }
        }
}