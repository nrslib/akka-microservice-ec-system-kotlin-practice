package com.example.shop.billing.api.consumer.billing

import akka.Done
import akka.actor.typed.ActorRef
import akka.cluster.sharding.typed.javadsl.EntityTypeKey
import akka.pattern.StatusReply


sealed interface BillingConsumerMessage {
    companion object {
        fun typekey() = EntityTypeKey.create(BillingConsumerMessage::class.java, "BillingConsumerMessage")
    }
}

data class WrappedRequest(val message: BillingConsumerMessage, val replyTo: ActorRef<StatusReply<Done>>) : BillingConsumerMessage

object Test : BillingConsumerMessage
