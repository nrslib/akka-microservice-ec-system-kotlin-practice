package com.example.shop.order.service

import akka.actor.typed.Behavior
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.Behaviors
import com.example.kafka.delivery.*
import com.example.saga.ServiceActorProvider
import com.example.shop.billing.api.billing.BillingServiceChannels
import com.example.shop.billing.api.billing.commands.BillingServiceCommand
import com.example.shop.order.api.order.OrderServiceChannels
import com.example.shop.order.api.order.replies.OrderCreateSagaReply
import com.example.shop.order.service.app.model.order.Order
import com.example.shop.order.service.handlers.OrderCreateSagaReplyHandler
import com.example.shop.order.service.rest.RestRoutes
import com.example.shop.order.service.saga.order.create.OrderCreateSaga
import com.example.shop.stock.api.stock.StockServiceChannels
import com.example.shop.stock.api.stock.commands.StockServiceCommand
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.util.*

object Guardian {
    fun create(): Behavior<Void> = Behaviors.setup { context ->
        val kafkaConfig = KafkaConfig.load(context.system.settings().config())
        initSharding(context, kafkaConfig)
        launchHandler(context, kafkaConfig)
        launchApp(context)

        Behaviors.empty()
    }

    private fun initSharding(context: ActorContext<*>, kafkaConfig: KafkaConfig) {
        val serviceActorProvider = createServiceProvider(kafkaConfig)
        OrderCreateSaga.initSharding(context, serviceActorProvider)
        Order.initSharding(context)
    }

    private fun launchHandler(context: ActorContext<*>, kafkaConfig: KafkaConfig) {


        val consumerName = "kafkaConsumer-${OrderCreateSagaReply::class.java.name}"
        val consumer = context.spawn(
            KafkaConsumer.create<OrderCreateSagaReply>(
                kafkaConfig,
                OrderServiceChannels.createOrderSagaReplyChannel
            ) { consumerContext, message ->
                val handler = consumerContext.spawn(
                    OrderCreateSagaReplyHandler.create(),
                    "orderCreateSagaReplyHandler-${UUID.randomUUID()}"
                )
                handler.tell(OrderCreateSagaReplyHandler.Handle(message))
            }, consumerName
        )
        consumer.tell(KafkaConsumer.Initialize)
    }

    private fun launchApp(context: ActorContext<*>) {
        val system = context.system

        val restRoutes = RestRoutes(context, jacksonObjectMapper().registerKotlinModule())
        val app = OrderServiceApp(system, system.settings().config(), restRoutes)
        app.start()
    }

    private fun createServiceProvider(kafkaConfig: KafkaConfig): ServiceActorProvider {
        val serviceActorProvider = ServiceActorProvider()

        serviceActorProvider.registerProvider(
            BillingServiceCommand::class.java,
            KafkaBridge.create<BillingServiceCommand>(kafkaConfig, BillingServiceChannels.commandChannel) {
                it.entityId
            }
        )

        serviceActorProvider.registerProvider(
            StockServiceCommand::class.java,
            KafkaBridge.create<StockServiceCommand>(kafkaConfig, StockServiceChannels.commandChannel) {
                it.entityId
            }
        )

        return serviceActorProvider
    }
}