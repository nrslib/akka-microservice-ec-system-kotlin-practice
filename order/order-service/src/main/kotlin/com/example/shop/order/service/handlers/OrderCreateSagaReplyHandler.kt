package com.example.shop.order.service.handlers

import akka.actor.typed.javadsl.AbstractBehavior
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.Behaviors
import akka.actor.typed.javadsl.Receive
import akka.cluster.sharding.typed.javadsl.ClusterSharding
import com.example.shop.order.api.order.replies.*
import com.example.shop.order.service.saga.order.create.OrderCreateSaga

class OrderCreateSagaReplyHandler(context: ActorContext<Message>) :
    AbstractBehavior<OrderCreateSagaReplyHandler.Message>(context) {
    companion object {
        fun create() = Behaviors.setup<Message> {
            OrderCreateSagaReplyHandler(it)
        }
    }

    sealed interface Message
    data class Handle(val message: OrderCreateSagaReply) : Message

    private val cluster = ClusterSharding.get(context.system)

    override fun createReceive(): Receive<Message> =
        newReceiveBuilder()
            .onMessage(Handle::class.java) {
                val sagaId = OrderCreateSaga.entityId(it.message.orderId)
                val saga = cluster.entityRefFor(OrderCreateSaga.typekey(), sagaId)

                when (it.message) {
                    is SecureInventoryReply -> {
                        saga.tell(OrderCreateSaga.ReceiveSecureInventoryReply(it.message.orderId, it.message))
                        this
                    }
                    is ApproveBillingReply -> {
                        saga.tell(
                            OrderCreateSaga.ReceiveApproveBillingReply(
                                it.message.orderId,
                                it.message
                            )
                        )
                        this
                    }
                    is ApproveBillingCompleted -> {
                        saga.tell(
                            OrderCreateSaga.ReceiveApproveBillingCompleted(
                                it.message.orderId,
                                it.message.billingId
                            )
                        )
                        this
                    }
                    is CancelSecureReply -> {
                        saga.tell(OrderCreateSaga.CancelInventoryReply(it.message.orderId, it.message.success))
                        this
                    }
                }
            }
            .build()
}