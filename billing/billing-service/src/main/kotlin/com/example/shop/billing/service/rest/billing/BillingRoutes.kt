package com.example.shop.billing.service.rest.billing

import akka.actor.typed.ActorRef
import akka.actor.typed.javadsl.ActorContext
import akka.http.javadsl.marshallers.jackson.Jackson
import akka.http.javadsl.model.StatusCodes
import akka.http.javadsl.server.Directives.*
import akka.http.javadsl.server.PathMatchers.segment
import akka.persistence.query.journal.leveldb.javadsl.LeveldbReadJournal
import com.example.kafka.delivery.KafkaProducers
import com.example.shop.billing.service.app.service.billing.BillingApplicationService
import com.example.shop.billing.service.rest.billing.get.BillingGetResponse
import com.example.shop.shared.id.UuidIdGenerator
import com.fasterxml.jackson.databind.ObjectMapper


class BillingRoutes(
    private val context: ActorContext<*>,
    private val readJournal: LeveldbReadJournal,
    private val objectMapper: ObjectMapper,
    private val kafkaProducers: ActorRef<KafkaProducers.Message>
) {
    private val timeout = context.system.settings().config().getDuration("service.ask-timeout")

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
                val service = generateBillingService()
                val future = service.get(billingId)

                onSuccess(future) {
                    val response = BillingGetResponse(it)
                    completeOK(response, Jackson.marshaller())
                }
            }
        }

    private fun getAll() =
        get {
            val service = generateBillingService()
            val future = service.getAll()

            onSuccess(future) {
                completeOK(it, Jackson.marshaller())
            }
        }

    private fun postApprove() =
        post {
            path(segment().slash("approve")) { billingId ->
                val service = generateBillingService()
                val future = service.approve(billingId)

                onSuccess(future) {
                    if (it.isSuccess) {
                        completeOK(billingId, Jackson.marshaller())
                    } else {
                        complete(StatusCodes.BAD_REQUEST, billingId)
                    }
                }
            }
        }

    private fun generateBillingService(): BillingApplicationService {
        return BillingApplicationService(context, UuidIdGenerator(), readJournal, kafkaProducers)
    }
}