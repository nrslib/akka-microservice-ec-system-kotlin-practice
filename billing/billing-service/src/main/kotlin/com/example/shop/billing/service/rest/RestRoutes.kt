package com.example.shop.billing.service.rest

import akka.actor.typed.ActorRef
import akka.actor.typed.javadsl.ActorContext
import akka.http.javadsl.server.Directives
import akka.persistence.query.journal.leveldb.javadsl.LeveldbReadJournal
import com.example.kafka.delivery.KafkaProducers
import com.example.shop.billing.service.rest.billing.BillingRoutes
import com.fasterxml.jackson.databind.ObjectMapper

class RestRoutes(
    context: ActorContext<*>,
    readJournal: LeveldbReadJournal,
    objectMapper: ObjectMapper,
    kafkaProducers: ActorRef<KafkaProducers.Message>
) {
    val billingRoute = BillingRoutes(context, readJournal, objectMapper, kafkaProducers)

    fun routes() = Directives.concat(
        billingRoute.routes()
    )
}