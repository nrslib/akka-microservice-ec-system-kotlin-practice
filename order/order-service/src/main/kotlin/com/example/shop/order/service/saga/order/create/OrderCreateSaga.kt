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
import com.example.shop.billing.api.consumer.billing.ApproveOrder
import com.example.shop.billing.api.consumer.billing.BillingServiceProxy
import com.example.shop.shared.persistence.JacksonSerializable


class OrderCreateSaga(
    private val context: ActorContext<Message>,
    sagaId: String
) : EventSourcedBehavior<OrderCreateSaga.Message, OrderCreateSaga.Event, OrderCreateSagaState>(
    PersistenceId.ofUniqueId(
        sagaId
    )
) {
    companion object {
        fun typekey() = EntityTypeKey.create(Message::class.java, "OrderCreateSaga")
        fun create(id: String): Behavior<Message> = Behaviors.setup {
            OrderCreateSaga(it, id)
        }

        fun initSharding(context: ActorContext<*>) {
            ClusterSharding.get(context.system).init(Entity.of(typekey()) {
                create(it.entityId)
            })
        }
    }

    sealed interface Message
    data class StartSaga(val orderId: String) : Message
    data class ApproveBilling(val orderId: String) : Message
    data class ApproveReply(val success: Boolean) : Message

    sealed interface Event : JacksonSerializable
    object Started : Event
    object Approved : Event
    object Rejected : Event

    val kafkaBootStrapServers = context.system.settings().config().getString("kafka.bootstrap-servers")

    override fun emptyState(): OrderCreateSagaState = OrderCreateSagaState("")

    override fun commandHandler(): CommandHandler<Message, Event, OrderCreateSagaState> =
        newCommandHandlerBuilder()
            .forAnyState()
            .onCommand(StartSaga::class.java) { _, (orderId) ->
                Effect().persist(Started).thenRun {
                    val producer = context.spawn(
                        KafkaProducer.create(BillingServiceProxy.topic, kafkaBootStrapServers),
                        "billingServiceProducer-$orderId"
                    )
                    producer.tell(KafkaProducer.Send(orderId, ApproveOrder(orderId)))
                }
            }
            .onCommand(ApproveBilling::class.java) { _, (orderId) ->
                Effect().none()
            }
            .onCommand(ApproveReply::class.java) { _, (success) ->
                Effect().persist(if (success) Approved else Rejected)
            }
            .build()

    override fun eventHandler(): EventHandler<OrderCreateSagaState, Event> =
        newEventHandlerBuilder()
            .forAnyState()
            .onEvent(Started::class.java) { state, _ ->
                state.approvalPending()
            }
            .onEvent(Approved::class.java) { state, _ ->
                state.approve()
            }
            .onEvent(Rejected::class.java) { state, _ ->
                state.rejected()
            }
            .build()
}