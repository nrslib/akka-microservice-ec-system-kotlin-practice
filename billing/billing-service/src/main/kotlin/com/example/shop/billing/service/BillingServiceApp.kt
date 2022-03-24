package com.example.shop.billing.service

import akka.actor.typed.ActorSystem
import akka.http.javadsl.Http
import com.example.shop.billing.service.rest.RestRoutes
import com.typesafe.config.Config

class BillingServiceApp(
    private val actorSystem: ActorSystem<*>,
    private val config: Config,
    private val restRoutes: RestRoutes
) {
    fun start() {
        val host = config.getString("http.host")
        val port = config.getInt("http.port")

        startServer(host, port)
    }

    private fun startServer(host: String, port: Int) {
        Http.get(actorSystem)
            .newServerAt(host, port)
            .bind(restRoutes.routes())
    }
}