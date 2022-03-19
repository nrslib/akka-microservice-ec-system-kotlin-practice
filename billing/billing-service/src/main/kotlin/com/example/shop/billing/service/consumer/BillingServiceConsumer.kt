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
import com.example.kafka.delivery.KafkaConfig
import com.example.kafka.serialization.PayloadDeserializer
import com.example.shop.billing.api.billing.BillingServiceChannels
import com.example.shop.billing.api.billing.commands.BillingServiceCommand
import com.example.shop.billing.service.handlers.BillingServiceCommandHandler
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import java.util.*
import java.util.concurrent.CompletableFuture

class BillingServiceConsumer(
    context: ActorContext<Message>,
    private val kafkaConfig: KafkaConfig
) : AbstractBehavior<BillingServiceConsumer.Message>(context) {
    companion object {
        fun create(kafkaConfig: KafkaConfig) =
            Behaviors.setup<Message> {
                BillingServiceConsumer(it, kafkaConfig)
            }

        fun typekey() = EntityTypeKey.create(Message::class.java, "BillingServiceConsumer")

        fun initSharding(system: ActorSystem<*>, kafkaConfig: KafkaConfig) {
            ClusterSharding.get(system).init(
                Entity.of(typekey()) {
                    create(kafkaConfig)
                }
            )
        }
    }

    sealed interface Message
    object Initialize : Message
    data class Received(val message: BillingServiceCommand) : Message

    override fun createReceive(): Receive<Message> =
        newReceiveBuilder()
            .onMessage(Initialize::class.java) {
                val kafkaConsumerSettings = ConsumerSettings.create(
                    context.system,
                    StringDeserializer(),
                    PayloadDeserializer<BillingServiceCommand>()
                )
                    .withBootstrapServers(kafkaConfig.bootstrapServers)
                    .withGroupId(BillingServiceChannels.commandChannel)
                    .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
                    .withProperties(kafkaConfig.properties)

                val committerSettings = CommitterSettings.create(context.system)
                Consumer.committableSource(kafkaConsumerSettings, Subscriptions.topics(BillingServiceChannels.commandChannel))
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
                val handler = context.spawn(BillingServiceCommandHandler.create(kafkaConfig), "billingServiceCommandHandler-$id")
                handler.tell(BillingServiceCommandHandler.Handle(it.message))

                this
            }
            .build()
}