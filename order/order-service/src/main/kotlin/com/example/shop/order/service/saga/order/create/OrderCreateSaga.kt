package com.example.shop.order.service.saga.order.create

import akka.actor.typed.Behavior
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.Behaviors
import akka.cluster.sharding.typed.javadsl.ClusterSharding
import akka.cluster.sharding.typed.javadsl.Entity
import akka.cluster.sharding.typed.javadsl.EntityTypeKey
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.javadsl.*
import com.example.kafka.delivery.KafkaProducer
import com.example.shop.billing.api.billing.BillingServiceChannels
import com.example.shop.order.api.order.models.OrderDetail
import com.example.shop.order.api.order.replies.*
import com.example.shop.shared.persistence.JacksonSerializable
import com.example.shop.stock.api.stock.StockServiceChannels
import com.example.shop.stock.api.stock.commands.SecureInventory
import java.time.Duration


class OrderCreateSaga(
    private val context: ActorContext<Command>,
    sagaId: String,
    private val createProducer: (topic: String) -> Behavior<KafkaProducer.Message>
) : EventSourcedBehavior<OrderCreateSaga.Command, OrderCreateSaga.Event, OrderCreateSagaState>(
    PersistenceId.ofUniqueId(
        sagaId
    )
) {
    companion object {
        fun typekey() = EntityTypeKey.create(Command::class.java, "OrderCreateSaga")

        fun entityId(orderId: String) = "orderCreateSaga-$orderId"

        fun create(id: String, createProducer: (topic: String) -> Behavior<KafkaProducer.Message>): Behavior<Command> =
            Behaviors.setup {
                OrderCreateSaga(it, id, createProducer)
            }

        fun initSharding(context: ActorContext<*>, createProducer: (topic: String) -> Behavior<KafkaProducer.Message>) {
            ClusterSharding.get(context.system).init(Entity.of(typekey()) {
                create(it.entityId, createProducer)
            })
        }
    }

    sealed interface Command
    data class StartOrder(val orderId: String, val orderDetail: OrderDetail) : Command

    data class SecureInventory(val orderId: String) : Command
    data class ReceiveSecureInventoryReply(val orderId: String, val reply: SecureInventoryReply) : Command

    data class ApproveBilling(val orderId: String) : Command
    data class ReceiveApproveBillingReply(val orderId: String, val reply: ApproveBillingReply) : Command

    data class ReceiveApproveBillingCompleted(val orderId: String, val billingId: String) : Command

    data class CancelInventoryReply(val orderId: String, val success: Boolean) : Command

    data class Retry(val state: Step, val count: Int, val recentTimeout: Long, val message: Any) : Command

    sealed interface Event : JacksonSerializable

    data class OrderStarted(val orderId: String, val orderDetail: OrderDetail) : Event

    object SecuringStarted : Event
    object SecuringSucceeded : Event
    object SecuringFailed : Event

    object ApprovalStarted : Event
    data class ApprovalSucceeded(val billingId: String) : Event
    object ApprovalCompleted : Event
    object Rejected : Event

    object CancelInventoryFailed : Event

    override fun emptyState(): OrderCreateSagaState = OrderCreateSagaState("", null)

    override fun commandHandler(): CommandHandler<Command, Event, OrderCreateSagaState> {
        val builder = newCommandHandlerBuilder()

        buildNone(builder)
        buildSecureInventory(builder)
        buildApproveBilling(builder)
        buildWaitApproval(builder)

        builder.forAnyState().onAnyCommand {state , command ->
            Effect().none().thenRun {
                context.system.log().debug("ignored. {state: $state, command: $command}")
            }
        }

        return builder.build()
    }

    private fun scheduleRetry(
        state: OrderCreateSagaState,
        message: Any
    ) {
        val timeoutSecond = 1L
        val timeout = Duration.ofSeconds(timeoutSecond)
        val retry = Retry(state.step, 0, timeoutSecond, message)
        context.system.scheduler().scheduleOnce(timeout, {
            context.self.tell(retry)
        }, context.system.executionContext())
    }

    private fun buildNone(builder: CommandHandlerBuilder<Command, Event, OrderCreateSagaState>) {
        builder.forState { it.step == Step.None }
            .onCommand(StartOrder::class.java) { _, (orderId, orderDetail) ->
                Effect().persist(OrderStarted(orderId, orderDetail))
                    .thenRun {
                        context.self.tell(SecureInventory(orderId))
                    }
            }
    }

    private fun buildSecureInventory(builder: CommandHandlerBuilder<Command, Event, OrderCreateSagaState>) {
        builder.forState { it.step == Step.SecureInventory }
            .onCommand(SecureInventory::class.java) { state, (orderId) ->
                Effect().persist(SecuringStarted).thenRun {
                    val message = SecureInventory(orderId, "test-id")
                    val producer = context.spawn(
                        createProducer(StockServiceChannels.commandChannel),
                        "stockServiceProducer-$orderId"
                    )
                    producer.tell(
                        KafkaProducer.Send(
                            orderId,
                            SecureInventory(orderId, "test-id")
                        )
                    )

                    scheduleRetry(state, message)
                }
            }
            .onCommand(ReceiveSecureInventoryReply::class.java) { _, message ->
                when (message.reply) {
                    is SecureInventorySucceeded ->
                        Effect().persist(SecuringSucceeded)
                            .thenRun {
                                context.self.tell(ApproveBilling(message.orderId))
                            }
                    is SecureInventoryFailed ->
                        Effect().persist(SecuringFailed)
                }
            }
            .onCommand(Retry::class.java, this::retry)
    }

    private fun buildApproveBilling(builder: CommandHandlerBuilder<Command, Event, OrderCreateSagaState>) {
        builder.forState { it.step == Step.ApproveBilling }
            .onCommand(ApproveBilling::class.java) { state, (orderId) ->
                Effect().persist(ApprovalStarted).thenRun {
                    val producer = context.spawn(
                        createProducer(BillingServiceChannels.commandChannel),
                        "billingServiceProducer-$orderId"
                    )
                    val command = state.makeApproveBillingCommand()
                    producer.tell(KafkaProducer.Send(orderId, command))

                    scheduleRetry(state, command)
                }
            }
            .onCommand(ReceiveApproveBillingReply::class.java) { state, command ->
                when(command.reply) {
                    is ApproveBillingReplySucceeded -> {
                        val billingId = command.reply.billingId
                        Effect().persist(ApprovalSucceeded(billingId))
                    }
                    is ApproveBillingReplyFailed -> TODO()
                }

            }
            .onCommand(Retry::class.java, this::retry)
    }

    private fun buildWaitApproval(builder: CommandHandlerBuilder<Command, Event, OrderCreateSagaState>) {
        builder.forState { it.step == Step.WaitApproval}
            .onCommand(ReceiveApproveBillingCompleted::class.java) { _, _ ->
                Effect().persist(ApprovalCompleted)
            }

    }

    private fun retry(state: OrderCreateSagaState, command: Retry): EffectBuilder<Event, OrderCreateSagaState>? {
        return Effect().none().thenRun {
            if (state.step != command.state) {
                return@thenRun
            }

            val maxChallenge = 10
            val maxTimeout = 60

            val nextCount = command.count + 1
            if (nextCount > maxChallenge) {
                return@thenRun
            }

            val nextDurationTime = command.recentTimeout * 2
            if (nextDurationTime > maxTimeout) {
                return@thenRun
            }

            val timeout = Duration.ofSeconds(nextDurationTime)
            val retry = Retry(state.step, nextCount, nextDurationTime, command.message)
            context.system.scheduler().scheduleOnce(timeout, {
                context.self.tell(retry)
            }, context.system.executionContext())
        }
    }

    override fun eventHandler(): EventHandler<OrderCreateSagaState, Event> {
        val builder = newEventHandlerBuilder()

        builder.forState{it.step == Step.None}
            .onEvent(OrderStarted::class.java) { _, event ->
                val state = OrderCreateSagaState(event.orderId, event.orderDetail)
                state.forwardStep()
            }

        builder.forState {it.step == Step.SecureInventory}
            .onEvent(SecuringStarted::class.java) { state, _ ->
                state.securingPending()
            }
            .onEvent(SecuringSucceeded::class.java) { state, _ ->
                state.forwardStep()
            }
            .onEvent(SecuringFailed::class.java) { state, _ ->
                state
            }

        builder.forState {it.step == Step.ApproveBilling}
            .onEvent(ApprovalStarted::class.java) { state, _ ->
                state.approvalPending()
            }
            .onEvent(ApprovalSucceeded::class.java) { state, event ->
                state.approveBilling(event.billingId)
                state.forwardStep()
            }

        builder.forState {it.step == Step.WaitApproval }
            .onEvent(ApprovalCompleted::class.java) { state, _ ->
                state.forwardStep()
            }
            .onEvent(Rejected::class.java) { state, _ ->
                state.rejected()
            }

        return builder.build()
    }
}