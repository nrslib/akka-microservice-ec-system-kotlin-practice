package com.example.shop.billing.service.consumer

import akka.actor.typed.ActorSystem
import akka.actor.typed.javadsl.AbstractBehavior
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.Behaviors
import akka.actor.typed.javadsl.Receive
import akka.cluster.sharding.typed.javadsl.ClusterSharding
import akka.cluster.sharding.typed.javadsl.Entity
import akka.pattern.StatusReply
import com.example.shop.billing.api.consumer.billing.BillingConsumerMessage
import com.example.shop.billing.api.consumer.billing.Test
import com.example.shop.billing.api.consumer.billing.WrappedRequest

class BillingServiceConsumer(
    context: ActorContext<BillingConsumerMessage>
) : AbstractBehavior<BillingConsumerMessage>(context) {
    companion object {
        fun create() =
            Behaviors.setup<BillingConsumerMessage> {
                BillingServiceConsumer(it)
            }

        fun initSharding(system: ActorSystem<*>) {
            ClusterSharding.get(system).init(
                Entity.of(BillingConsumerMessage.typekey()) {
                    create()
                }
            )
        }
    }

    override fun createReceive(): Receive<BillingConsumerMessage> =
        newReceiveBuilder()
            .onMessage(WrappedRequest::class.java) {
                context.self.tell(it.message)
                it.replyTo.tell(StatusReply.ack())

                this
            }
            .onMessage(Test::class.java) {
                println("ok")

                this
            }
            .build()
}