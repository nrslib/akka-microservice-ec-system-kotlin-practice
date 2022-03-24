package com.example.shop.stock.service

import akka.actor.typed.Behavior
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.Behaviors
import com.example.kafka.delivery.KafkaConfig
import com.example.kafka.delivery.KafkaConsumer
import com.example.shop.stock.api.stock.StockServiceChannels
import com.example.shop.stock.api.stock.commands.StockServiceCommand
import com.example.shop.stock.service.handlers.StockServiceCommandHandler
import java.util.*

object Guardian {
    fun create(): Behavior<Void> = Behaviors.setup { context ->
        val kafkaConfig = KafkaConfig.load(context.system.settings().config())

        launchHandler(context, kafkaConfig)

        Behaviors.empty()
    }

    private fun launchHandler(context: ActorContext<*>, kafkaConfig: KafkaConfig) {
        val consumerName = "kafkaConsumer-${StockServiceCommand::class.java.name}"
        val consumer = context.spawn(
            KafkaConsumer.create<StockServiceCommand>(
                kafkaConfig,
                StockServiceChannels.commandChannel
            ) { consumerContext, message ->
                val handler = consumerContext.spawn(
                    StockServiceCommandHandler.create(kafkaConfig),
                    "stockServiceCommandHandler-${UUID.randomUUID()}"
                )
                handler.tell(StockServiceCommandHandler.Handle(message))
            }, consumerName
        )
        consumer.tell(KafkaConsumer.Initialize)
    }
}