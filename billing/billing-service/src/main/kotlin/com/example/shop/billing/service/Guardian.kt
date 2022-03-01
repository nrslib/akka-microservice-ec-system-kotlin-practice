package com.example.shop.billing.service

import akka.actor.typed.Behavior
import akka.actor.typed.delivery.ConsumerController
import akka.actor.typed.javadsl.Behaviors
import akka.cluster.sharding.typed.delivery.ShardingConsumerController
import akka.cluster.sharding.typed.javadsl.ClusterSharding
import akka.cluster.sharding.typed.javadsl.Entity
import akka.cluster.sharding.typed.javadsl.EntityTypeKey
import akka.cluster.typed.Cluster
import com.example.shop.billing.api.consumer.billing.BillingConsumerMessage
import com.example.shop.billing.service.consumer.BillingServiceConsumer

object Guardian {
    fun create(): Behavior<Void> = Behaviors.setup {
        BillingServiceConsumer.initSharding(it.system)

        Behaviors.empty()
    }
}