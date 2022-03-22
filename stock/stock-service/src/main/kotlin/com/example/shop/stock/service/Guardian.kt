package com.example.shop.stock.service

import akka.actor.typed.Behavior
import akka.actor.typed.javadsl.Behaviors
import akka.cluster.sharding.typed.javadsl.ClusterSharding
import com.example.kafka.delivery.KafkaConfig
import com.example.kafka.delivery.KafkaConsumer
import com.example.shop.stock.api.stock.StockServiceChannels
import com.example.shop.stock.api.stock.commands.StockServiceCommand
import com.example.shop.stock.service.handlers.StockServiceCommandHandler
import java.util.*

object Guardian {
    fun create(): Behavior<Void> = Behaviors.setup { context ->
        val kafkaConfig = KafkaConfig.load(context.system.settings().config())
        val consumerName = "stockServiceConsumer-${StockServiceCommand::class.java.name}"
        KafkaConsumer.initSharding<StockServiceCommand>(
            context.system,
            consumerName,
            kafkaConfig,
            StockServiceChannels.commandChannel
        ) { consumerContext, message ->
            val handler = consumerContext.spawn(
                StockServiceCommandHandler.create(kafkaConfig),
                "billingServiceCommandHandler-${UUID.randomUUID()}"
            )
            handler.tell(StockServiceCommandHandler.Handle(message))
        }
        val consumer = ClusterSharding.get(context.system)
            .entityRefFor(KafkaConsumer.typekey(consumerName), "billing-consumer-1")
        consumer.tell(KafkaConsumer.Initialize)

        Behaviors.empty()
    }
}