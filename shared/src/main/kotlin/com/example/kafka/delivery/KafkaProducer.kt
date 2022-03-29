package com.example.kafka.delivery

import akka.actor.typed.javadsl.AbstractBehavior
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.Behaviors
import akka.actor.typed.javadsl.Receive
import akka.kafka.ProducerMessage
import akka.kafka.ProducerSettings
import akka.kafka.javadsl.Producer
import akka.stream.javadsl.Sink
import akka.stream.javadsl.Source
import com.example.kafka.serialization.PayloadSerializer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer

class KafkaProducer(context: ActorContext<Message>, private val topic: String, private val kafkaConfig: KafkaConfig) :
    AbstractBehavior<KafkaProducer.Message>(context) {
    companion object {
        fun create(topic: String, kafkaConfig: KafkaConfig) = Behaviors.setup<Message> {
            KafkaProducer(it, topic, kafkaConfig)
        }
    }

    sealed interface Message
    data class Send<T>(val id: String, val message: T) : Message

    override fun createReceive(): Receive<Message> =
        newReceiveBuilder()
            .onMessage(Send::class.java) { (id, message) ->
                val kafkaProducerSettings = ProducerSettings
                    .create(context.system, StringSerializer(), PayloadSerializer())
                    .withProperties(kafkaConfig.properties)
                    .withBootstrapServers(kafkaConfig.bootstrapServers)

                Source.single(
                    ProducerMessage.single(
                        ProducerRecord(
                            topic,
                            id,
                            message
                        )
                    )
                )
                    .via(Producer.flexiFlow(kafkaProducerSettings))
                    .log("test")
                    .runWith(Sink.foreach {
                        println("===== Kafka Producer End log begin =====")
                        println(it)
                        println("===== Kafka Producer End log end =====")
                    }, context.system)

                this
            }
            .build()
}