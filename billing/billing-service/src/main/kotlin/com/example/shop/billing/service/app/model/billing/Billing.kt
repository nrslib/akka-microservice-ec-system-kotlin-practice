package com.example.shop.billing.service.app.model.billing

import akka.actor.typed.ActorRef
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
import com.example.shop.shared.persistence.JacksonSerializable

class Billing(billingId: String) :
    EventSourcedBehavior<Billing.Command, Billing.Event, Billing.State>(PersistenceId.ofUniqueId(billingId)) {
    companion object {
        fun typekey() = EntityTypeKey.create(Command::class.java, "Billing")

        fun create(billingId: String) = Behaviors.setup<Command> {
            Billing(billingId)
        }

        fun initSharding(context: ActorContext<*>) {
            ClusterSharding.get(context.system).init(Entity.of(typekey()) {
                create(it.entityId)
            })
        }
    }

    sealed interface Command
    sealed interface Event : JacksonSerializable

    data class OpenBilling(val consumerId: String, val orderId: String) : Command
    data class BillingOpened(val consumerId: String, val orderId: String) : Event

    data class GetDetail(val replyTo: ActorRef<BillingDetail>) : Command

    data class Approve(val replyTo: ActorRef<StatusReply<String>>) : Command
    object Approved : Event

    data class State(val consumerId: String, val orderId: String, val status: Status) : JacksonSerializable {
        enum class Status {
            Pending,
            Approved
        }

        fun approve() = copy(status = Status.Approved)
    }


    override fun emptyState(): State = State("", "", State.Status.Pending)

    override fun commandHandler(): CommandHandler<Command, Event, State> =
        newCommandHandlerBuilder()
            .forAnyState()
            .onCommand(OpenBilling::class.java) { _, (consumerId, orderId) ->
                Effect().persist(BillingOpened(consumerId, orderId))
            }
            .onCommand(GetDetail::class.java) { state, (replyTo) ->
                Effect().none()
                    .thenReply(replyTo) {
                        BillingDetail(persistenceId().entityId(), state.consumerId, state.orderId)
                    }
            }
            .onCommand(Approve::class.java) { state, (replyTo) ->
                Effect().persist(Approved).thenReply(replyTo) {
                    StatusReply.success(state.orderId)
                }
            }
            .build()

    override fun eventHandler(): EventHandler<State, Event> =
        newEventHandlerBuilder()
            .forAnyState()
            .onEvent(BillingOpened::class.java) { _, (consumerId, orderId) ->
                State(consumerId, orderId, State.Status.Pending)
            }
            .onEvent(Approved::class.java) { state, _ ->
                state.approve()
            }
            .build()
}