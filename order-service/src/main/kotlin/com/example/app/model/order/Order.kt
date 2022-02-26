package com.example.app.model.order

import akka.Done
import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.Behaviors
import akka.cluster.sharding.typed.javadsl.ClusterSharding
import akka.cluster.sharding.typed.javadsl.Entity
import akka.cluster.sharding.typed.javadsl.EntityTypeKey
import akka.pattern.StatusReply
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.javadsl.CommandHandler
import akka.persistence.typed.javadsl.EventHandler
import akka.persistence.typed.javadsl.EventSourcedBehavior
import com.example.app.service.order.OrderService
import com.example.shared.persistence.JacksonSerializable

class Order(
    id: String,
    private val shard: ActorRef<ClusterSharding.ShardCommand>,
    private val context: ActorContext<Command>
) :
    EventSourcedBehavior<Order.Command, Order.Event, OrderState>(PersistenceId.ofUniqueId(id)) {
    companion object {
        fun typekey() = EntityTypeKey.create(Command::class.java, "Order")
        fun create(id: String, shard: ActorRef<ClusterSharding.ShardCommand>): Behavior<Command> = Behaviors.setup {
            Order(id, shard, it)
        }

        fun initSharding(system: ActorSystem<*>) {
            ClusterSharding.get(system).init(Entity.of(typekey()) {
                create(it.entityId, it.shard)
            }.withStopMessage(Stop))
        }
    }

    object Stop : Command

    sealed interface Command
    object Create : Command
    data class Get(val replyTo: ActorRef<OrderState>) : Command
    data class Approve(val replyTo: ActorRef<StatusReply<Done>>) : Command
    data class Reject(val replyTo: ActorRef<StatusReply<Done>>) : Command
    data class Cancel(val replyTo: ActorRef<OrderService.Message>) : Command

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
            .onCommand(Get::class.java) {state, (replyTo) ->
                Effect().none().thenReply(replyTo) {
                    state
                }
            }
            .onCommand(Create::class.java) { _, _ ->
                Effect().persist(Created)
            }
            .onCommand(Approve::class.java) { state, (replyTo) ->
                if (state.canActivate()) {
                    Effect().persist(Approved).thenReply(replyTo) {
                        StatusReply.ack()
                    }
                } else {
                    Effect().none().thenReply(replyTo) {
                        StatusReply.error("current state: ${state.orderState}")
                    }
                }
            }
            .onCommand(Reject::class.java) { state, (replyTo) ->
                if (state.canReject()) {
                    Effect().persist(Approved).thenReply(replyTo) {
                        StatusReply.ack()
                    }
                } else {
                    Effect().none().thenReply(replyTo) {
                        StatusReply.error("current state: ${state.orderState}")
                    }
                }
            }
            .onCommand(Cancel::class.java) { state, (replyTo) ->
                if (state.canCancel()) {
                    Effect().persist(Canceled)
                        .thenReply(replyTo) { OrderService.CancelOrderReply(true) }
                } else {
                    Effect().none()
                        .thenReply(replyTo) { OrderService.CancelOrderReply(false) }
                }
            }
            .onCommand(Stop::class.java) { _, _ ->
                Effect().none().thenRun {
                    shard.tell(ClusterSharding.Passivate(context.self))
                }.thenStop()
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