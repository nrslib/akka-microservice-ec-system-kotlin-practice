package com.example.shop.order.service.producer

import akka.actor.typed.ActorRef
import akka.actor.typed.javadsl.AbstractBehavior
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.Behaviors
import akka.actor.typed.javadsl.Receive
import akka.cluster.sharding.typed.ShardingEnvelope
import akka.cluster.sharding.typed.delivery.ShardingProducerController
import com.example.shop.order.service.consumer.billing.BillingServiceForwarder

class BillingServiceProducer(
    context: ActorContext<Message>,
    private val billingServiceConsumerEntityId: String,
    private val producerController: ActorRef<ShardingProducerController.Command<BillingServiceForwarder.Message>>,
) : AbstractBehavior<BillingServiceProducer.Message>(context) {
    companion object {
        fun create(
            producerController: ActorRef<ShardingProducerController.Command<BillingServiceForwarder.Message>>,
            billingServiceEntityId: String
        ) = Behaviors.setup<Message> {
            BillingServiceProducer(it, billingServiceEntityId, producerController)
        }
    }

    sealed interface Message
    data class Start(val command: BillingServiceForwarder.Message) : Message
    data class WrappedRequestNext(
        val next: ShardingProducerController.RequestNext<BillingServiceForwarder.Message>,
        val command: BillingServiceForwarder.Message
    ) :
        Message

    override fun createReceive(): Receive<Message> =
        newReceiveBuilder()
            .onMessage(Start::class.java) { (command) ->
                val requestNextAdapter: ActorRef<ShardingProducerController.RequestNext<BillingServiceForwarder.Message>> =
                    context.messageAdapter(ShardingProducerController.requestNextClass()) {
                        WrappedRequestNext(it, command)
                    }

                producerController.tell(ShardingProducerController.Start(requestNextAdapter))

                this
            }
            .onMessage(WrappedRequestNext::class.java) { (next, command) ->
                val envelope = ShardingEnvelope(billingServiceConsumerEntityId, command)
                next.sendNextTo().tell(envelope)

                Behaviors.stopped()
            }
            .build()

}

