package com.example.shop.order.service.saga.order.create

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
import com.example.kafka.delivery.KafkaProducer
import com.example.shop.billing.api.billing.BillingServiceChannels
import com.example.shop.billing.api.billing.commands.ApproveOrder
import com.example.shop.shared.persistence.JacksonSerializable
import com.example.shop.stock.api.stock.StockServiceChannels


class OrderCreateSaga(
    private val context: ActorContext<Message>,
    sagaId: String,
    private val createProducer: (topic: String) -> Behavior<KafkaProducer.Message>
) : EventSourcedBehavior<OrderCreateSaga.Message, OrderCreateSaga.Event, OrderCreateSagaState>(
    PersistenceId.ofUniqueId(
        sagaId
    )
) {
    companion object {
        fun typekey() = EntityTypeKey.create(Message::class.java, "OrderCreateSaga")

        fun entityId(orderId: String) = "OrderCreateSaga-$orderId"

        fun create(id: String, createProducer: (topic: String) -> Behavior<KafkaProducer.Message>): Behavior<Message> =
            Behaviors.setup {
                OrderCreateSaga(it, id, createProducer)
            }

        fun initSharding(context: ActorContext<*>, createProducer: (topic: String) -> Behavior<KafkaProducer.Message>) {
            ClusterSharding.get(context.system).init(Entity.of(typekey()) {
                create(it.entityId, createProducer)
            })
        }
    }

    sealed interface Message
    data class StartSaga(val orderId: String) : Message
    data class SecureInventory(val orderId: String) : Message
    data class SecureInventoryReply(val orderId: String, val success: Boolean) : Message
    data class ApproveBilling(val orderId: String) : Message
    data class ApproveReply(val orderId: String, val success: Boolean) : Message
    data class CancelInventoryReply(val orderId: String, val success: Boolean) : Message

    sealed interface Event : JacksonSerializable

    object SecuringStarted : Event
    object SecuringFailed : Event

    object ApprovalStarted : Event
    object ApprovalSucceeded : Event
    object Rejected : Event

    object CancelInventoryFailed : Event

    override fun emptyState(): OrderCreateSagaState = OrderCreateSagaState("")

    override fun commandHandler(): CommandHandler<Message, Event, OrderCreateSagaState> =
        newCommandHandlerBuilder()
            .forAnyState()
            .onCommand(StartSaga::class.java) { _, (orderId) ->
                Effect().none().thenRun {
                    context.self.tell(SecureInventory(orderId))
                }
            }
            .onCommand(SecureInventory::class.java) { _, (orderId) ->
                Effect().persist(SecuringStarted).thenRun {
                    val producer = context.spawn(
                        createProducer(StockServiceChannels.commandChannel),
                        "stockServiceProducer-$orderId"
                    )
                    producer.tell(
                        KafkaProducer.Send(
                            orderId,
                            com.example.shop.stock.api.stock.commands.SecureInventory(orderId, "test-id")
                        )
                    )
                }
            }
            .onCommand(SecureInventoryReply::class.java) { _, (orderId, success) ->
                if (success) {
                    Effect().none().thenRun {
                        context.self.tell(ApproveBilling(orderId))
                    }
                } else {
                    Effect().persist(SecuringFailed)
                }
            }
            .onCommand(ApproveBilling::class.java) { _, (orderId) ->
                Effect().persist(ApprovalStarted).thenRun {
                    val producer = context.spawn(
                        createProducer(BillingServiceChannels.commandChannel),
                        "billingServiceProducer-$orderId"
                    )
                    producer.tell(KafkaProducer.Send(orderId, ApproveOrder(orderId)))
                }
            }
            .onCommand(ApproveReply::class.java) { _, (orderId, success) ->
                Effect().persist(if (success) ApprovalSucceeded else Rejected)
            }
            .build()

    override fun eventHandler(): EventHandler<OrderCreateSagaState, Event> =
        newEventHandlerBuilder()
            .forAnyState()
            .onEvent(SecuringStarted::class.java) { state, _ ->
                state.securingPending()
            }
            .onEvent(ApprovalStarted::class.java) { state, _ ->
                state.approvalPending()
            }
            .onEvent(ApprovalSucceeded::class.java) { state, _ ->
                state.approve()
            }
            .onEvent(Rejected::class.java) { state, _ ->
                state.rejected()
            }
            .build()
}