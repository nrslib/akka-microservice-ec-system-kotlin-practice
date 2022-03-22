package com.example.kafka.delivery

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
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import java.util.concurrent.CompletableFuture

class KafkaConsumer<T>(
    context: ActorContext<Message>,
    private val kafkaConfig: KafkaConfig,
    private val topic: String,
    private val handler: (context: ActorContext<*>, message: T) -> Unit
) : AbstractBehavior<KafkaConsumer.Message>(context) {
    companion object {
        fun <T> create(
            kafkaConfig: KafkaConfig,
            topic: String,
            handler: (context: ActorContext<*>, message: T) -> Unit
        ) = Behaviors.setup<Message> {
            KafkaConsumer(it, kafkaConfig, topic, handler)
        }

        fun typekey(name: String) = EntityTypeKey.create(Message::class.java, name)

        fun <T> initSharding(
            system: ActorSystem<*>,
            name: String,
            kafkaConfig: KafkaConfig,
            topic: String,
            handler: (context: ActorContext<*>, message: T) -> Unit
        ) {
            ClusterSharding.get(system).init(
                Entity.of(typekey(name)) {
                    create(kafkaConfig, topic, handler)
                }
            )
        }
    }

    sealed interface Message
    object Initialize : Message
    data class Received<T>(val message: T) : Message

    override fun createReceive(): Receive<Message> =
        newReceiveBuilder()
            .onMessage(Initialize::class.java) {
                val kafkaConsumerSettings = ConsumerSettings.create(
                    context.system,
                    StringDeserializer(),
                    PayloadDeserializer<T>()
                )
                    .withBootstrapServers(kafkaConfig.bootstrapServers)
                    .withGroupId(topic)
                    .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
                    .withProperties(kafkaConfig.properties)

                val committerSettings = CommitterSettings.create(context.system)
                Consumer.committableSource(
                    kafkaConsumerSettings,
                    Subscriptions.topics(topic)
                )
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
            .onMessage(Received::class.java) { (message) ->
                handler(context, message as T)

                this
            }
            .build()
}