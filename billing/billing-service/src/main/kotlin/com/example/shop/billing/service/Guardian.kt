package com.example.shop.billing.service

import akka.actor.typed.Behavior
import akka.actor.typed.javadsl.Behaviors
import akka.cluster.sharding.typed.javadsl.ClusterSharding
import com.example.kafka.delivery.KafkaConfig
import com.example.kafka.delivery.KafkaConsumer
import com.example.shop.billing.api.billing.BillingServiceChannels
import com.example.shop.billing.api.billing.commands.BillingServiceCommand
import com.example.shop.billing.service.handlers.BillingServiceCommandHandler
import java.util.*

object Guardian {
    fun create(): Behavior<Void> = Behaviors.setup { context ->
        val kafkaConfig = KafkaConfig.load(context.system.settings().config())
        val consumerName = "billingServiceConsumer-${BillingServiceCommand::class.java.name}"
        KafkaConsumer.initSharding<BillingServiceCommand>(
            context.system,
            consumerName,
            kafkaConfig,
            BillingServiceChannels.commandChannel
        ) { consumerContext, message ->
            val handler = consumerContext.spawn(
                BillingServiceCommandHandler.create(kafkaConfig),
                "billingServiceCommandHandler-${UUID.randomUUID()}"
            )
            handler.tell(BillingServiceCommandHandler.Handle(message))
        }
        val consumer = ClusterSharding.get(context.system)
            .entityRefFor(KafkaConsumer.typekey(consumerName), "billing-consumer-1")
        consumer.tell(KafkaConsumer.Initialize)

        Behaviors.empty()
    }
}