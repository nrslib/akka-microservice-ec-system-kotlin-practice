package com.example.shop.billing.api.consumer.billing

import akka.Done
import akka.actor.typed.ActorRef
import akka.cluster.sharding.typed.javadsl.EntityTypeKey
import akka.pattern.StatusReply


sealed interface BillingServiceMessage {
    companion object {
        fun typekey() = EntityTypeKey.create(BillingServiceMessage::class.java, "BillingConsumerMessage")
    }
}

data class WrappedRequest(val message: BillingServiceMessage, val replyTo: ActorRef<StatusReply<Done>>) :
    BillingServiceMessage

object Test : BillingServiceMessage
data class ApproveOrder(val orderId: String) : BillingServiceMessage
