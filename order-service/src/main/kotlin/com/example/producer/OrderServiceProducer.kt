package com.example.producer

import akka.actor.typed.ActorRef
import akka.actor.typed.javadsl.AbstractBehavior
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.Behaviors
import akka.actor.typed.javadsl.Receive
import akka.cluster.sharding.typed.ShardingEnvelope
import akka.cluster.sharding.typed.delivery.ShardingProducerController
import com.example.consumer.order.OrderServiceConsumer

class OrderServiceProducer(
    context: ActorContext<Message>,
    private val consumerEntityId: String,
    private val producerController: ActorRef<ShardingProducerController.Command<OrderServiceConsumer.Message>>,

    ) : AbstractBehavior<OrderServiceProducer.Message>(context) {
    companion object {
        fun create(
            producerController: ActorRef<ShardingProducerController.Command<OrderServiceConsumer.Message>>,
            orderServiceConsumerEntityId: String
        ) = Behaviors.setup<Message> {
            OrderServiceProducer(it, orderServiceConsumerEntityId, producerController)
        }
    }

    sealed interface Message
    data class Start(val command: OrderServiceConsumer.Message) : Message
    data class WrappedRequestNext(
        val next: ShardingProducerController.RequestNext<OrderServiceConsumer.Message>,
        val command: OrderServiceConsumer.Message
    ) :
        Message

    override fun createReceive(): Receive<Message> =
        newReceiveBuilder()
            .onMessage(Start::class.java) { (command) ->
                val requestNextAdapter: ActorRef<ShardingProducerController.RequestNext<OrderServiceConsumer.Message>> =
                    context.messageAdapter(ShardingProducerController.requestNextClass()) {
                        WrappedRequestNext(it, command)
                    }
                producerController.tell(ShardingProducerController.Start(requestNextAdapter))

                this
            }
            .onMessage(WrappedRequestNext::class.java) { (next, command) ->
                val envelope = ShardingEnvelope(consumerEntityId, command)
                next.sendNextTo().tell(envelope)

                Behaviors.stopped()
            }
            .build()

}

