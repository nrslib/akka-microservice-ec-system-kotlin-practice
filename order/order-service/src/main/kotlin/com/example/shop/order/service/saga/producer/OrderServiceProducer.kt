//package com.example.saga.producer
//
//import akka.actor.typed.Behavior
//import akka.actor.typed.delivery.ProducerController
//import akka.actor.typed.javadsl.AbstractBehavior
//import akka.actor.typed.javadsl.ActorContext
//import akka.actor.typed.javadsl.Behaviors
//import akka.actor.typed.javadsl.Receive
//import OrderService
//
//class OrderServiceProducer(context: ActorContext<Command>) : AbstractBehavior<OrderServiceProducer.Command>(context) {
//    companion object {
//        fun create(): Behavior<Command> = Behaviors.setup {
//            OrderServiceProducer(it)
//        }
//    }
//
//    sealed interface Command
//    data class Send(val message: OrderService.Message) : Command
//    data class WrappedRequestNext(val next: ProducerController.RequestNext<OrderService.Message>) : Command
//
//    override fun createReceive(): Receive<Command> =
//        newReceiveBuilder()
//}