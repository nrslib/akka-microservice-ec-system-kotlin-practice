package com.example.shop.billing.service

import akka.actor.typed.Behavior
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.Behaviors
import akka.persistence.query.PersistenceQuery
import akka.persistence.query.journal.leveldb.javadsl.LeveldbReadJournal
import com.example.kafka.delivery.KafkaConfig
import com.example.kafka.delivery.KafkaConsumer
import com.example.kafka.delivery.KafkaProducers
import com.example.shop.billing.api.billing.BillingServiceChannels
import com.example.shop.billing.api.billing.commands.BillingServiceCommand
import com.example.shop.billing.service.app.model.billing.Billing
import com.example.shop.billing.service.handlers.BillingServiceCommandHandler
import com.example.shop.billing.service.rest.RestRoutes
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.util.*

object Guardian {
    fun create(): Behavior<Void> = Behaviors.setup { context ->
        val kafkaConfig = KafkaConfig.load(context.system.settings().config())
        val readJournal = PersistenceQuery.get(context.system).getReadJournalFor(
            LeveldbReadJournal::class.java, LeveldbReadJournal.Identifier()
        )

        initSharding(context)
        launchHandler(context, kafkaConfig, readJournal)
        launchApp(context, kafkaConfig, readJournal)

        Behaviors.empty()
    }

    private fun initSharding(context: ActorContext<*>) {
        Billing.initSharding(context)
    }

    private fun launchHandler(context: ActorContext<*>, kafkaConfig: KafkaConfig, readJournal: LeveldbReadJournal) {
        val consumerName = "kafkaConsumer-${BillingServiceCommand::class.java.name}"
        val consumer = context.spawn(
            KafkaConsumer.create<BillingServiceCommand>(
                kafkaConfig,
                BillingServiceChannels.commandChannel
            ) { consumerContext, message ->
                val handler = consumerContext.spawn(
                    BillingServiceCommandHandler.create(kafkaConfig, readJournal),
                    "billingServiceCommandHandler-${UUID.randomUUID()}"
                )
                handler.tell(BillingServiceCommandHandler.Handle(message))
            }, consumerName
        )
        consumer.tell(KafkaConsumer.Initialize)
    }

    private fun launchApp(context: ActorContext<*>, kafkaConfig: KafkaConfig, readJournal: LeveldbReadJournal) {
        val system = context.system

        val kafkaProducers = context.spawn(KafkaProducers.create(kafkaConfig), "kafkaProducers-${UUID.randomUUID()}")
        val restRoutes = RestRoutes(context, readJournal, jacksonObjectMapper().registerKotlinModule(), kafkaProducers)
        val app = BillingServiceApp(system, system.settings().config(), restRoutes)
        app.start()
    }
}