package com.example.shop.order.service

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.delivery.ConsumerController
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.Behaviors
import akka.cluster.sharding.typed.delivery.ShardingConsumerController
import akka.cluster.sharding.typed.delivery.ShardingProducerController
import akka.cluster.sharding.typed.javadsl.ClusterSharding
import akka.cluster.sharding.typed.javadsl.Entity
import akka.cluster.sharding.typed.javadsl.EntityTypeKey
import akka.cluster.typed.Cluster
import com.example.shop.billing.api.consumer.billing.BillingConsumerMessage
import com.example.shop.order.service.app.provider.OrderServiceProvider
import com.example.shop.order.service.app.service.order.OrderService
import com.example.shop.order.service.consumer.order.OrderServiceConsumer
import com.example.shop.order.service.saga.order.create.OrderCreateSaga
import com.example.shop.order.service.app.model.order.Order
import com.example.shop.order.service.consumer.billing.BillingServiceForwarder
import com.example.shop.shared.id.FixedIdGenerator
import java.util.*

object Guardian {
    fun create(): Behavior<Void> = Behaviors.setup {
        val selfAddress = Cluster.get(it.system).selfMember().address()
        val hostAndPort = "${selfAddress.host.get()}:${selfAddress.port.get()}"
        val actorNameSuffix = "-fromGuardian-$hostAndPort"

        val clusterSharding = ClusterSharding.get(it.system)

        val orderServiceProducerControllerEntityId = "orderServiceProducerControllerEntityId"
        val orderServiceProvider = OrderServiceProvider(FixedIdGenerator("test-order-id"))
        val orderProducerController = createOrderProducerController(it, clusterSharding, orderServiceProducerControllerEntityId, orderServiceProvider, actorNameSuffix)

        val billingServiceProducerControllerEntityId = "billingServiceProducerControllerEntityId"
        val billingProducerController = createBillingProducerController(it, clusterSharding, billingServiceProducerControllerEntityId, actorNameSuffix)

        OrderCreateSaga.initSharding(it,orderProducerController, billingProducerController,  orderServiceProducerControllerEntityId, billingServiceProducerControllerEntityId)

        Order.initSharding(it.system)

        val orderService = it.spawn(orderServiceProvider.provide(), "orderService$actorNameSuffix")
        val printer = it.spawn(
            Behaviors.receive(OrderService.CreateOrderReply::class.java)
                .onMessage(OrderService.CreateOrderReply::class.java) { reply ->
                    println(reply)
                    Behaviors.same()
                }.build(), "printer$actorNameSuffix"
        )

        orderService.tell(OrderService.CreateOrder("test", printer))

        Behaviors.empty()
    }

    private fun createOrderProducerController(
        context: ActorContext<Void>,
        clusterSharding: ClusterSharding,
        orderServiceConsumerControllerId: String,
        orderServiceProvider: OrderServiceProvider,
        suffix: String):
            ActorRef<ShardingProducerController.Command<OrderServiceConsumer.Message>> {
        val orderServiceConsumerControllerEntityTypeKey: EntityTypeKey<ConsumerController.SequencedMessage<OrderServiceConsumer.Message>> =
            EntityTypeKey.create(
                ShardingConsumerController.entityTypeKeyClass(),
                orderServiceConsumerControllerId
            )

        val orderServiceRegion = clusterSharding
            .init(Entity.of(orderServiceConsumerControllerEntityTypeKey) { entityContext ->
                ShardingConsumerController.create { start ->
                    OrderServiceConsumer.create(orderServiceProvider, entityContext.entityId, start)
                }
            })

        val orderServiceProducerControllerId = "orderServiceProducerController$suffix"
        val orderProducerController = context.spawn(
            ShardingProducerController.create(
                OrderServiceConsumer.Message::class.java,
                orderServiceProducerControllerId,
                orderServiceRegion,
                Optional.empty()
            ),
            "producerController$suffix"
        )

        return orderProducerController
    }

    private fun createBillingProducerController(
        context: ActorContext<Void>,
        clusterSharding: ClusterSharding,
        consumerControllerId: String,
        suffix: String
    ) : ActorRef<ShardingProducerController.Command<BillingServiceForwarder.Message>> {
        val orderServiceConsumerControllerEntityTypeKey: EntityTypeKey<ConsumerController.SequencedMessage<BillingServiceForwarder.Message>> =
            EntityTypeKey.create(
                ShardingConsumerController.entityTypeKeyClass(),
                consumerControllerId
            )

        val orderServiceRegion = clusterSharding
            .init(Entity.of(orderServiceConsumerControllerEntityTypeKey) { entityContext ->
                ShardingConsumerController.create { start ->
                    BillingServiceForwarder.create(start, "billingServiceConsumer")
                }
            })



        val billingServiceProducerControllerId = "billingServiceProducerController$suffix"
        return context.spawn(
            ShardingProducerController.create(
                BillingServiceForwarder.Message::class.java,
                billingServiceProducerControllerId,
                orderServiceRegion,
                Optional.empty()
            ),
            "billingServiceProducerController$suffix"
        )
    }
}