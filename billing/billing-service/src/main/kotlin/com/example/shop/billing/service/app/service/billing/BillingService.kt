package com.example.shop.billing.service.app.service.billing

import akka.Done
import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.javadsl.AbstractBehavior
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.Behaviors
import akka.actor.typed.javadsl.Receive
import akka.pattern.StatusReply
import com.example.shop.shared.id.IdGenerator

class BillingService(context: ActorContext<Message>) : AbstractBehavior<BillingService.Message>(context) {
    companion object {
        fun create(idGenerator: IdGenerator): Behavior<Message> = Behaviors.setup {
            BillingService(it)
        }
    }

    sealed interface Message
    data class ApproveOrder(val orderId: String, val replyTo: ActorRef<StatusReply<Done>>) : Message

    override fun createReceive(): Receive<Message> =
        newReceiveBuilder()
            .onMessage(ApproveOrder::class.java) {
                println("approving")

                it.replyTo.tell(StatusReply.Ack())

                this
            }
            .build()
}