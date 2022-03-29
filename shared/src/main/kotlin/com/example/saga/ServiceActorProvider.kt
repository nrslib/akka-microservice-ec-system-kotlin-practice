package com.example.saga

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.javadsl.ActorContext

class ServiceActorProvider {
    private val typeToProvider = mutableMapOf<Class<*>, Behavior<*>>()

    fun registerProvider(messageClazz: Class<*>, behavior: Behavior<*>) {
        typeToProvider[messageClazz] = behavior
    }

    fun <Message>spawn(context: ActorContext<*>, messageClazz: Class<Message>, name: String): ActorRef<Message> {
        val provider = getProvider(messageClazz)

        return context.spawn(provider, name)
    }

    private fun <Message> getProvider(messageClazz: Class<Message>): Behavior<Message> {
        val provider = typeToProvider[messageClazz] ?: throw IllegalStateException("Unregistered message: $messageClazz")

        return provider as Behavior<Message>
    }
}