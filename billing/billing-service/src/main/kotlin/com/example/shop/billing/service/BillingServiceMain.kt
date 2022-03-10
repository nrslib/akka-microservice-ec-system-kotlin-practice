package com.example.shop.billing.service

import akka.actor.setup.ActorSystemSetup
import akka.actor.typed.ActorSystem
import akka.serialization.jackson.JacksonObjectMapperProviderSetup
import com.example.shop.shared.persistence.KotlinModuleJacksonObjectMapperFactory

fun main() {
    val setup = ActorSystemSetup.empty().withSetup(
        JacksonObjectMapperProviderSetup(KotlinModuleJacksonObjectMapperFactory())
    )
    ActorSystem.create(Guardian.create(), "billing-service", setup)
}