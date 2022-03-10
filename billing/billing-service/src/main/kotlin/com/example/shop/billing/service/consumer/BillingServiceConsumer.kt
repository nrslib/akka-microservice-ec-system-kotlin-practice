package com.example.shop.billing.service.consumer

import akka.actor.typed.ActorSystem
import akka.actor.typed.javadsl.AbstractBehavior
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.Behaviors
import akka.actor.typed.javadsl.Receive
import akka.cluster.sharding.typed.javadsl.ClusterSharding
import akka.cluster.sharding.typed.javadsl.Entity
import akka.cluster.sharding.typed.javadsl.EntityTypeKey
import akka.kafka.CommitterSettings
import akka.kafka.ConsumerSettings
import akka.kafka.Subscriptions
import akka.kafka.javadsl.Committer
import akka.kafka.javadsl.Consumer
import com.example.kafka.serialization.PayloadDeserializer
import com.example.shop.billing.api.consumer.billing.BillingServiceMessage
import com.example.shop.billing.service.app.service.billing.BillingService
import com.example.shop.shared.id.UuidIdGenerator
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import java.util.*
import java.util.concurrent.CompletableFuture

class BillingServiceConsumer(
    context: ActorContext<Message>
) : AbstractBehavior<BillingServiceConsumer.Message>(context) {
    companion object {
        fun create() =
            Behaviors.setup<Message> {
                BillingServiceConsumer(it)
            }

        fun typekey() = EntityTypeKey.create(Message::class.java, "BillingServiceConsumer")

        fun initSharding(system: ActorSystem<*>) {
            ClusterSharding.get(system).init(
                Entity.of(typekey()) {
                    create()
                }
            )
        }
    }

    sealed interface Message
    object Initialize : Message
    data class Received(val message: BillingServiceMessage) : Message

    override fun createReceive(): Receive<Message> =
        newReceiveBuilder()
            .onMessage(Initialize::class.java) {
                val bootstrapServers = "localhost:9092"

                val topic = "billing-service-topic"
                val kafkaConsumerSettings = ConsumerSettings.create(
                    context.system,
                    StringDeserializer(),
                    PayloadDeserializer<BillingServiceMessage>()
                )
                    .withBootstrapServers(bootstrapServers)
                    .withGroupId(topic)
                    .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")

                val committerSettings = CommitterSettings.create(context.system)
                Consumer.committableSource(kafkaConsumerSettings, Subscriptions.topics(topic))
                    .mapAsync(1) {
                        CompletableFuture.supplyAsync {
                            context.self.tell(Received(it.record().value()))
                        }
                            .thenApply { _ ->
                                it.committableOffset()
                            }
                    }
                    .toMat(Committer.sink(committerSettings), Consumer::createDrainingControl)
                    .run(context.system)

                this
            }
            .onMessage(Received::class.java) {
                val id = UUID.randomUUID().toString()
                val service = context.spawn(BillingService.create(UuidIdGenerator()), "billingService-$id")
                service.tell(it.message)

                this
            }
            .build()
}