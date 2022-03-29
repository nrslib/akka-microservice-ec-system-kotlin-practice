package com.example.kafka.delivery

import akka.actor.typed.javadsl.AbstractBehavior
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.Behaviors
import akka.actor.typed.javadsl.Receive
import java.util.*

class KafkaBridge<Message>(
    context: ActorContext<Message>,
    private val messageClazz: Class<Message>,
    private val kafkaConfig: KafkaConfig,
    private val topic: String,
    private val getIdentifier: (m: Message) -> String) : AbstractBehavior<Message>(context) {
    companion object {
        inline fun <reified Message> create(kafkaConfig: KafkaConfig, topic: String, noinline getIdentifier: (m: Message) -> String) = Behaviors.setup<Message> {
            KafkaBridge(it, Message::class.java, kafkaConfig, topic, getIdentifier)
        }
    }

    override fun createReceive(): Receive<Message> =
        newReceiveBuilder()
            .onMessage(messageClazz) { message ->
                val producer =
                    context.spawn(KafkaProducer.create(topic, kafkaConfig), "kafkaProducer-${UUID.randomUUID()}")

                val entityId = getIdentifier(message)
                producer.tell(KafkaProducer.Send(entityId, message))

                this
            }
            .build()

}