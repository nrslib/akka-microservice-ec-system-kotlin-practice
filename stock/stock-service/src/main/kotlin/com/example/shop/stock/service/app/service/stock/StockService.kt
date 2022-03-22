package com.example.shop.stock.service.app.service.stock

import akka.Done
import akka.actor.typed.ActorRef
import akka.actor.typed.javadsl.AbstractBehavior
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.Behaviors
import akka.actor.typed.javadsl.Receive
import akka.pattern.StatusReply

class StockService(context: ActorContext<Message>) : AbstractBehavior<StockService.Message>(context) {
    companion object {
        fun create() = Behaviors.setup<Message> {
            StockService(it)
        }
    }

    sealed interface Message
    data class SecureInventory(val orderId: String, val itemId: String, val replyTo: ActorRef<StatusReply<Done>>) :
        Message

    data class CancelSecure(val orderId: String, val replyTo: ActorRef<StatusReply<Done>>) : Message

    override fun createReceive(): Receive<Message> =
        newReceiveBuilder()
            .onMessage(SecureInventory::class.java) {
                println("secured")

                it.replyTo.tell(StatusReply.Ack())

                this
            }
            .onMessage(CancelSecure::class.java) {
                println("canceled")

                it.replyTo.tell(StatusReply.Ack())

                this
            }
            .build()
}