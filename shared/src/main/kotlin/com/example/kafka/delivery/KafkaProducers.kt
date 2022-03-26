package com.example.kafka.delivery

import akka.actor.typed.javadsl.AbstractBehavior
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.Behaviors
import akka.actor.typed.javadsl.Receive
import java.util.*

class KafkaProducers(context: ActorContext<Message>, private val kafkaConfig: KafkaConfig) :
    AbstractBehavior<KafkaProducers.Message>(
        context
    ) {
    companion object {
        fun create(kafkaConfig: KafkaConfig) = Behaviors.setup<Message> {
            KafkaProducers(it, kafkaConfig)
        }
    }

    sealed interface Message
    data class Send(val topic: String, val id: String, val message: Any) : Message

    override fun createReceive(): Receive<Message> =
        newReceiveBuilder()
            .onMessage(Send::class.java) { message ->
                val producer = context.spawn(
                    KafkaProducer.create(message.topic, kafkaConfig),
                    "kafkaProducer-${UUID.randomUUID()}"
                )
                producer.tell(KafkaProducer.Send(message.id, message.message))

                this
            }
            .build()
}