package com.example

import akka.actor.typed.Behavior
import akka.actor.typed.delivery.ConsumerController
import akka.actor.typed.javadsl.Behaviors
import akka.cluster.sharding.typed.delivery.ShardingConsumerController
import akka.cluster.sharding.typed.delivery.ShardingProducerController
import akka.cluster.sharding.typed.javadsl.ClusterSharding
import akka.cluster.sharding.typed.javadsl.Entity
import akka.cluster.sharding.typed.javadsl.EntityTypeKey
import akka.cluster.typed.Cluster
import com.example.app.model.order.Order
import com.example.app.provider.OrderServiceProvider
import com.example.app.service.order.OrderService
import com.example.consumer.order.OrderServiceConsumer
import com.example.saga.order.create.OrderCreateSaga
import com.example.shared.id.FixedIdGenerator
import java.util.*

object Guardian {
    fun create(): Behavior<Void> = Behaviors.setup {
        val selfAddress = Cluster.get(it.system).selfMember().address()
        val hostAndPort = "${selfAddress.host.get()}:${selfAddress.port.get()}"
        val actorNameSuffix = "-fromGuardian-$hostAndPort"

        val orderServiceConsumerControllerEntityId = "orderServiceConsumerController$actorNameSuffix"
        val orderServiceConsumerControllerEntityTypeKey: EntityTypeKey<ConsumerController.SequencedMessage<OrderServiceConsumer.Message>> =
            EntityTypeKey.create(
                ShardingConsumerController.entityTypeKeyClass(),
                orderServiceConsumerControllerEntityId
            )

        val clusterSharding = ClusterSharding.get(it.system)

        val orderServiceProvider = OrderServiceProvider(FixedIdGenerator("test-order-id"))

        val orderServiceRegion = clusterSharding
            .init(Entity.of(orderServiceConsumerControllerEntityTypeKey) { entityContext ->
                ShardingConsumerController.create { start ->
                    OrderServiceConsumer.create(orderServiceProvider, entityContext.entityId, start)
                }
            })

        val producerId = "OrderCreateSagaProducerController$actorNameSuffix"
        val producerController = it.spawn(
            ShardingProducerController.create(
                OrderServiceConsumer.Message::class.java,
                producerId,
                orderServiceRegion,
                Optional.empty()
            ),
            "producerController$actorNameSuffix"
        )
        OrderCreateSaga.initSharding(it, orderServiceConsumerControllerEntityId, producerController)

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
}