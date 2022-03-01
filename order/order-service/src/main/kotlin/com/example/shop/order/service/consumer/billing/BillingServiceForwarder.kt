package com.example.shop.order.service.consumer.billing

import akka.Done
import akka.actor.typed.ActorRef
import akka.actor.typed.delivery.ConsumerController
import akka.actor.typed.javadsl.*
import akka.cluster.sharding.typed.javadsl.ClusterSharding
import akka.pattern.StatusReply
import com.example.shop.billing.api.consumer.billing.BillingConsumerMessage
import com.example.shop.billing.api.consumer.billing.WrappedRequest
import java.time.Duration

class BillingServiceForwarder(
    context: ActorContext<Message>,
    private val consumerController: ActorRef<ConsumerController.Start<Message>>,
    private val consumerEntityId: String
) : AbstractBehavior<BillingServiceForwarder.Message>(context){
    companion object {
        fun create(
            consumerController: ActorRef<ConsumerController.Start<Message>>,
            billingServiceConsumerEntityId: String,
        ) =
            Behaviors.setup<Message> {
                it.self.tell(Initialize)

                BillingServiceForwarder(it, consumerController, billingServiceConsumerEntityId)
            }
    }

    sealed interface Message
    object Initialize : Message
    data class CommandDelivery(val message: Message, val confirmTo: ActorRef<ConsumerController.Confirmed>) : Message
    data class SendMessage(val consumerMessage: BillingConsumerMessage): Message

    val clusterSharding = ClusterSharding.get(context.system)

    override fun createReceive(): Receive<Message> =
        newReceiveBuilder()
            .onMessage(Initialize::class.java) {
                val deliveryAdapter =
                    context.messageAdapter<ConsumerController.Delivery<Message>>(ConsumerController.deliveryClass()) {
                        CommandDelivery(it.message(), it.confirmTo())
                    }
                consumerController.tell(ConsumerController.Start(deliveryAdapter))

                this
            }
            .onMessage(CommandDelivery::class.java) {
                val billingConsumer = clusterSharding.entityRefFor(
                    BillingConsumerMessage.typekey(),
                    consumerEntityId
                )
                val sendMessage = it.message as SendMessage

                AskPattern.ask(
                    billingConsumer,
                    {replyTo: ActorRef<StatusReply<Done>> -> WrappedRequest(sendMessage.consumerMessage, replyTo) },
                    Duration.ofSeconds(5),
                    context.system.scheduler()
                ).whenComplete { done, exception ->
                    if (done != null) {
                        it.confirmTo.tell(ConsumerController.confirmed())
                    }
                }

                this
            }
            .onMessage(SendMessage::class.java) {
                val billingConsumer = clusterSharding.entityRefFor(
                    BillingConsumerMessage.typekey(),
                    consumerEntityId
                )
                billingConsumer.tell(it.consumerMessage)

                this
            }
            .build()
}