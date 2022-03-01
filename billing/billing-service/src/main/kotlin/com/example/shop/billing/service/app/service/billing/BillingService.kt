package com.example.shop.billing.service.app.service.billing

import akka.actor.typed.Behavior
import akka.actor.typed.javadsl.AbstractBehavior
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.Behaviors
import akka.actor.typed.javadsl.Receive
import com.example.shop.billing.api.service.message.BillingServiceMessage
import com.example.shop.billing.api.service.message.Test
import com.example.shop.shared.id.IdGenerator

class BillingService(context: ActorContext<BillingServiceMessage>) : AbstractBehavior<BillingServiceMessage>(context) {
    companion object {
        fun create(idGenerator: IdGenerator): Behavior<BillingServiceMessage> = Behaviors.setup {
            BillingService(it)
        }
    }

    override fun createReceive(): Receive<BillingServiceMessage> =
        newReceiveBuilder()
            .onMessage(Test::class.java) {
                println("test")

                this
            }
            .build()
}