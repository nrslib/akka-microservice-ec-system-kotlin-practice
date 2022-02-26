package com.example.saga.order.create

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.Behaviors
import akka.cluster.sharding.typed.delivery.ShardingProducerController
import akka.cluster.sharding.typed.javadsl.ClusterSharding
import akka.cluster.sharding.typed.javadsl.Entity
import akka.cluster.sharding.typed.javadsl.EntityTypeKey
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.javadsl.CommandHandler
import akka.persistence.typed.javadsl.EventHandler
import akka.persistence.typed.javadsl.EventSourcedBehavior
import com.example.consumer.order.OrderServiceConsumer
import com.example.producer.OrderServiceProducer
import com.example.shared.persistence.JacksonSerializable


class OrderCreateSaga(
    private val context: ActorContext<Message>,
    sagaId: String,
    private val consumerEntityId: String,
    private val orderServiceProducer: ActorRef<ShardingProducerController.Command<OrderServiceConsumer.Message>>
) : EventSourcedBehavior<OrderCreateSaga.Message, OrderCreateSaga.Event, OrderCreateSagaState>(
    PersistenceId.ofUniqueId(
        sagaId
    )
) {
    companion object {
        fun typekey() = EntityTypeKey.create(Message::class.java, "OrderCreateSaga")
        fun create(
            id: String,
            orderServiceEntityId: String,
            orderServiceProducer: ActorRef<ShardingProducerController.Command<OrderServiceConsumer.Message>>
        ): Behavior<Message> = Behaviors.setup {
            OrderCreateSaga(it, id, orderServiceEntityId, orderServiceProducer)
        }

        fun initSharding(
            context: ActorContext<*>,
            consumerEntityId: String,
            producerController: ActorRef<ShardingProducerController.Command<OrderServiceConsumer.Message>>
        ) {
            ClusterSharding.get(context.system).init(Entity.of(typekey()) {
                create(it.entityId, consumerEntityId, producerController)
            })
        }
    }

    sealed interface Message
    data class StartSaga(val orderId: String) : Message
    data class ApproveReply(val success: Boolean) : Message

    sealed interface Event : JacksonSerializable
    object Started : Event
    object Approved : Event
    object Rejected : Event

    override fun emptyState(): OrderCreateSagaState = OrderCreateSagaState("")

    override fun commandHandler(): CommandHandler<Message, Event, OrderCreateSagaState> =
        newCommandHandlerBuilder()
            .forAnyState()
            .onCommand(StartSaga::class.java) { _, (orderId) ->
                Effect().persist(Started).thenRun {
                    val producer = context.spawn(
                        OrderServiceProducer.create(orderServiceProducer, consumerEntityId),
                        "orderServiceProducer-$consumerEntityId"
                    )

                    producer.tell(OrderServiceProducer.Start(OrderServiceConsumer.Approved(orderId, context.self)))
                }
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