package com.example.shop.billing.service

import akka.actor.typed.Behavior
import akka.actor.typed.javadsl.Behaviors
import akka.cluster.sharding.typed.javadsl.ClusterSharding
import com.example.kafka.delivery.KafkaConfig
import com.example.kafka.delivery.KafkaProducer
import com.example.shop.billing.service.consumer.BillingServiceConsumer
import com.example.shop.billing.service.handlers.BillingServiceCommandHandler
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.common.config.SaslConfigs

object Guardian {
    fun create(): Behavior<Void> = Behaviors.setup { context ->
        BillingServiceConsumer.initSharding(context.system, KafkaConfig.load(context.system.settings().config()))

        val consumer = ClusterSharding.get(context.system)
            .entityRefFor(BillingServiceConsumer.typekey(), "test-consumer-1")
        consumer.tell(BillingServiceConsumer.Initialize)

        Behaviors.empty()
    }
}