package com.example.shop.order.service

import akka.actor.typed.Behavior
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.Behaviors
import akka.cluster.sharding.typed.javadsl.ClusterSharding
import akka.cluster.typed.Cluster
import com.example.kafka.delivery.KafkaConfig
import com.example.kafka.delivery.KafkaConsumer
import com.example.kafka.delivery.KafkaProducer
import com.example.shop.order.api.order.OrderServiceChannels
import com.example.shop.order.api.order.replies.OrderCreateSagaReply
import com.example.shop.order.service.app.model.order.Order
import com.example.shop.order.service.app.service.order.OrderService
import com.example.shop.order.service.handlers.OrderCreateSagaReplyHandler
import com.example.shop.order.service.rest.RestRoutes
import com.example.shop.order.service.saga.order.create.OrderCreateSaga
import com.example.shop.shared.id.UuidIdGenerator
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.util.*

object Guardian {
    fun create(): Behavior<Void> = Behaviors.setup { context ->
        val kafkaConfig = KafkaConfig.load(context.system.settings().config())
        initSharding(context, kafkaConfig)

        launchHandler(context, kafkaConfig)

        val system = context.system

        val selfAddress = Cluster.get(system).selfMember().address()
        val hostAndPort = "${selfAddress.host.get()}:${selfAddress.port.get()}"
        val actorNameSuffix = "-fromGuardian-$hostAndPort"
        val service = context.spawn(OrderService.create(UuidIdGenerator()), "orderService$actorNameSuffix")

        val restRoutes = RestRoutes(system, jacksonObjectMapper().registerKotlinModule(), service)
        val app = OrderServiceApp(system, system.settings().config(), restRoutes)
        app.start()

        Behaviors.empty()
    }

    private fun initSharding(context: ActorContext<*>, kafkaConfig: KafkaConfig) {
        OrderCreateSaga.initSharding(context) { topic ->
            KafkaProducer.create(topic, kafkaConfig)
        }
        Order.initSharding(context)
    }

    private fun launchHandler(context: ActorContext<*>, kafkaConfig: KafkaConfig) {
        val consumerName = "kafkaConsumer-${OrderCreateSagaReply::class.java.name}"
        KafkaConsumer.initSharding<OrderCreateSagaReply>(
            context.system,
            consumerName,
            kafkaConfig,
            OrderServiceChannels.createOrderSagaReplyChannel
        ) { consumerContext, message ->
            val handler = consumerContext.spawn(
                OrderCreateSagaReplyHandler.create(),
                "orderCreateSagaReplyHandler-${UUID.randomUUID()}"
            )
            handler.tell(OrderCreateSagaReplyHandler.Handle(message))
        }
        val consumer = ClusterSharding.get(context.system)
            .entityRefFor(KafkaConsumer.typekey(consumerName), "createOrderSagaReply-kafkaConsumer-1")
        consumer.tell(KafkaConsumer.Initialize)
    }
}