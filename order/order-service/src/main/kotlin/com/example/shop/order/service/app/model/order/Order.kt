package com.example.shop.order.service.app.model.order

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.Behaviors
import akka.cluster.sharding.typed.javadsl.ClusterSharding
import akka.cluster.sharding.typed.javadsl.Entity
import akka.cluster.sharding.typed.javadsl.EntityTypeKey
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.javadsl.CommandHandler
import akka.persistence.typed.javadsl.EventHandler
import akka.persistence.typed.javadsl.EventSourcedBehavior
import com.example.shop.shared.persistence.JacksonSerializable


class Order(id: String) :
    EventSourcedBehavior<Order.Command, Order.Event, OrderState>(PersistenceId.ofUniqueId(id)) {
    companion object {
        fun typekey() = EntityTypeKey.create(Command::class.java, "Order")
        fun create(id: String): Behavior<Command> = Behaviors.setup {
            Order(id)
        }

        fun initSharding(context: ActorContext<*>) {
            ClusterSharding.get(context.system).init(Entity.of(typekey()) {
                create(it.entityId)
            })
        }
    }

    sealed interface Command

    data class CreateOrder(val replyTo: ActorRef<CreateOrderReply>) : Command
    sealed interface CreateOrderReply
    object CreateOrderSucceeded : CreateOrderReply
    data class CreateOrderFailed(val causeBy: Throwable) : CreateOrderReply

    data class Get(val replyTo: ActorRef<OrderState>) : Command

    data class Approve(val replyTo: ActorRef<ApproveReply>) : Command
    sealed interface ApproveReply
    object ApproveSucceeded : ApproveReply
    data class ApproveFailed(val causeBy: Throwable) : ApproveReply

    data class Reject(val replyTo: ActorRef<RejectReply>) : Command
    sealed interface RejectReply
    object RejectSucceeded : RejectReply
    data class RejectFailed(val causeBy: Throwable) : RejectReply

    data class Cancel(val replyTo: ActorRef<CancelReply>) : Command
    sealed interface CancelReply
    object CancelSucceeded : CancelReply
    data class CancelFailed(val causeBy: Throwable) : CancelReply

    sealed interface Event : JacksonSerializable
    object Created : Event
    object Approved : Event
    object Rejected : Event
    object Canceled : Event

    override fun emptyState(): OrderState {
        return OrderState()
    }

    override fun commandHandler(): CommandHandler<Command, Event, OrderState> =
        newCommandHandlerBuilder()
            .forAnyState()
            .onCommand(CreateOrder::class.java) { state, command ->
                if (state.canActivate()) {
                    Effect().persist(Created).thenReply(command.replyTo) {
                        CreateOrderSucceeded
                    }
                } else {
                    Effect().none().thenReply(command.replyTo) {
                        CreateOrderFailed(IllegalStateChangeException(state.orderState))
                    }
                }
            }
            .onCommand(Get::class.java) { state, (replyTo) ->
                Effect().none().thenReply(replyTo) {
                    state
                }
            }
            .onCommand(Approve::class.java) { state, (replyTo) ->
                if (state.canApprove()) {
                    Effect().persist(Approved).thenReply(replyTo) {
                        ApproveSucceeded
                    }
                } else {
                    Effect().none().thenReply(replyTo) {
                        ApproveFailed(IllegalStateChangeException(state.orderState))
                    }
                }
            }
            .onCommand(Reject::class.java) { state, (replyTo) ->
                if (state.canReject()) {
                    Effect().persist(Approved).thenReply(replyTo) {
                        RejectSucceeded
                    }
                } else {
                    Effect().none().thenReply(replyTo) {
                        RejectFailed(IllegalStateChangeException(state.orderState))
                    }
                }
            }
            .onCommand(Cancel::class.java) { state, (replyTo) ->
                if (state.canCancel()) {
                    Effect().persist(Canceled).thenReply(replyTo) {
                        CancelSucceeded
                    }
                } else {
                    Effect().none().thenReply(replyTo) {
                        CancelFailed(IllegalStateChangeException(state.orderState))
                    }
                }
            }
            .build()

    override fun eventHandler(): EventHandler<OrderState, Event> =
        newEventHandlerBuilder()
            .forAnyState()
            .onEvent(Created::class.java) { state, _ ->
                state.activate()
            }
            .onEvent(Approved::class.java) { state, _ ->
                state.approve()
            }
            .onEvent(Rejected::class.java) { state, _ ->
                state.reject()
            }
            .onEvent(Canceled::class.java) { state, _ ->
                state.cancel()
            }
            .build()
}