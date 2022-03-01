package com.example.shop.order.service.saga.order.create

import akka.Done
import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.AskPattern
import akka.actor.typed.javadsl.Behaviors
import akka.cluster.sharding.typed.delivery.ShardingProducerController
import akka.cluster.sharding.typed.javadsl.ClusterSharding
import akka.cluster.sharding.typed.javadsl.Entity
import akka.cluster.sharding.typed.javadsl.EntityTypeKey
import akka.pattern.StatusReply
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.javadsl.CommandHandler
import akka.persistence.typed.javadsl.EventHandler
import akka.persistence.typed.javadsl.EventSourcedBehavior
import com.example.shop.billing.api.consumer.billing.Test
import com.example.shop.order.service.consumer.billing.BillingServiceForwarder
import com.example.shop.order.service.consumer.order.OrderServiceConsumer
import com.example.shop.order.service.producer.BillingServiceProducer
import com.example.shop.order.service.producer.OrderServiceProducer
import com.example.shop.shared.persistence.JacksonSerializable
import java.time.Duration


class OrderCreateSaga(
    private val context: ActorContext<Message>,
    sagaId: String,
    private val orderServiceProducer: ActorRef<ShardingProducerController.Command<OrderServiceConsumer.Message>>,
    private val billingServiceProducer: ActorRef<ShardingProducerController.Command<BillingServiceForwarder.Message>>,
    private val orderServiceConsumerEntityId: String,
    private val billingServiceConsumerEntityId: String
) : EventSourcedBehavior<OrderCreateSaga.Message, OrderCreateSaga.Event, OrderCreateSagaState>(
    PersistenceId.ofUniqueId(
        sagaId
    )
) {
    companion object {
        fun typekey() = EntityTypeKey.create(Message::class.java, "OrderCreateSaga")
        fun create(
            id: String,
            orderServiceProducer: ActorRef<ShardingProducerController.Command<OrderServiceConsumer.Message>>,
            billingServiceProducer: ActorRef<ShardingProducerController.Command<BillingServiceForwarder.Message>>,
            orderServiceConsumerEntityId: String,
            billingServiceConsumerEntityId: String
        ): Behavior<Message> = Behaviors.setup {
            OrderCreateSaga(
                it,
                id,
                orderServiceProducer,
                billingServiceProducer,
                orderServiceConsumerEntityId,
                billingServiceConsumerEntityId
            )
        }

        fun initSharding(
            context: ActorContext<*>,
            orderServiceProducerController: ActorRef<ShardingProducerController.Command<OrderServiceConsumer.Message>>,
            billingServiceProducerController: ActorRef<ShardingProducerController.Command<BillingServiceForwarder.Message>>,
            orderServiceConsumerEntityId: String,
            billingServiceConsumerEntityId: String
        ) {
            ClusterSharding.get(context.system).init(Entity.of(typekey()) {
                create(
                    it.entityId,
                    orderServiceProducerController,
                    billingServiceProducerController,
                    orderServiceConsumerEntityId,
                    billingServiceConsumerEntityId
                )
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

    override fun emptyState(): OrderCreateSagaState = OrderCreateSagaState("")

    override fun commandHandler(): CommandHandler<Message, Event, OrderCreateSagaState> =
        newCommandHandlerBuilder()
            .forAnyState()
            .onCommand(StartSaga::class.java) { _, (orderId) ->
                Effect().persist(Started).thenRun {
//                    val producer = context.spawn(
//                        OrderServiceProducer.create(orderServiceProducer, orderServiceConsumerEntityId),
//                        "orderServiceProducer-$orderServiceConsumerEntityId"
//                    )
//
//                    producer.tell(OrderServiceProducer.Start(OrderServiceConsumer.Approved(orderId, context.self)))
                    val producer = context.spawn(
                        BillingServiceProducer.create(billingServiceProducer, billingServiceConsumerEntityId),
                        "billingServiceProducer-$orderServiceConsumerEntityId"
                    )
                    producer.tell(
                        BillingServiceProducer.Start(
                            BillingServiceForwarder.SendMessage(
                                Test
                            )
                        )
                    )
                }
            }
            .onCommand(ApproveBilling::class.java) { _, (orderId) ->
                Effect().none().thenRun {
                    val producer = context.spawn(
                        BillingServiceProducer.create(billingServiceProducer, billingServiceConsumerEntityId),
                        "billingServiceProducer-$orderServiceConsumerEntityId"
                    )
                    producer.tell(
                        BillingServiceProducer.Start(
                            BillingServiceForwarder.SendMessage(
                                Test
                            )
                        )
                    )
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