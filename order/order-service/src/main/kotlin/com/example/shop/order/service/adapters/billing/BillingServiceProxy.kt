package com.example.shop.order.service.adapters.billing

import akka.actor.typed.javadsl.AbstractBehavior
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.Behaviors
import akka.actor.typed.javadsl.Receive
import com.example.kafka.delivery.KafkaConfig
import com.example.kafka.delivery.KafkaProducer
import com.example.shop.billing.api.billing.BillingServiceChannels
import com.example.shop.billing.api.billing.commands.BillingServiceCommand
import java.util.UUID

class BillingServiceProxy(context: ActorContext<BillingServiceCommand>, private val kafkaConfig: KafkaConfig) : AbstractBehavior<BillingServiceCommand>(
    context
) {
    companion object {
        fun create(kafkaConfig: KafkaConfig) = Behaviors.setup<BillingServiceCommand> {
            BillingServiceProxy(it, kafkaConfig)
        }
    }

    override fun createReceive(): Receive<BillingServiceCommand> =
        newReceiveBuilder()
            .onMessage(BillingServiceCommand::class.java) { message ->
                val producer = context.spawn(KafkaProducer.create(BillingServiceChannels.commandChannel, kafkaConfig), "kafkaProducer-billingService${UUID.randomUUID()}")
                producer.tell(KafkaProducer.Send(message.entityId, message))

                this
            }
            .build()
}