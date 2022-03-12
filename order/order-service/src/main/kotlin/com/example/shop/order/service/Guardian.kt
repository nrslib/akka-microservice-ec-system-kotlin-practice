package com.example.shop.order.service

import akka.actor.typed.Behavior
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.Behaviors
import akka.cluster.typed.Cluster
import com.example.kafka.delivery.KafkaProducer
import com.example.shop.order.service.app.model.order.Order
import com.example.shop.order.service.app.provider.OrderServiceProvider
import com.example.shop.order.service.app.service.order.OrderService
import com.example.shop.order.service.rest.RestRoutes
import com.example.shop.order.service.saga.order.create.OrderCreateSaga
import com.example.shop.shared.id.FixedIdGenerator
import com.example.shop.shared.id.UuidIdGenerator
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.common.config.SaslConfigs

object Guardian {
    fun create(): Behavior<Void> = Behaviors.setup { context ->
        initSharding(context)

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

    private fun initSharding(context: ActorContext<*>) {
        OrderCreateSaga.initSharding(context) { topic ->
            val kafkaBootstrapServers = context.system.settings().config().getString("kafka.bootstrap-servers")
            KafkaProducer.create(
                topic, kafkaBootstrapServers, mapOf(
                    Pair(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_SSL"),
                    Pair(SaslConfigs.SASL_MECHANISM, "AWS_MSK_IAM"),
                    Pair(
                        SaslConfigs.SASL_JAAS_CONFIG,
                        "software.amazon.msk.auth.iam.IAMLoginModule required awsProfileName=\"default\";"
                    ),
                    Pair(
                        SaslConfigs.SASL_CLIENT_CALLBACK_HANDLER_CLASS,
                        "software.amazon.msk.auth.iam.IAMClientCallbackHandler"
                    )
                )
            )
        }
        Order.initSharding(context)
    }
}