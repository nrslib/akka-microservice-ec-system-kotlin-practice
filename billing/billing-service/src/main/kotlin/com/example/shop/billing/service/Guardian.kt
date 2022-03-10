package com.example.shop.billing.service

import akka.actor.typed.Behavior
import akka.actor.typed.javadsl.Behaviors
import akka.cluster.sharding.typed.javadsl.ClusterSharding
import com.example.shop.billing.service.consumer.BillingServiceConsumer

object Guardian {
    fun create(): Behavior<Void> = Behaviors.setup {
        BillingServiceConsumer.initSharding(it.system)
        val consumer = ClusterSharding.get(it.system)
            .entityRefFor(BillingServiceConsumer.typekey(), "test-consumer-1")
        consumer.tell(BillingServiceConsumer.Initialize)

        Behaviors.empty()
    }
}