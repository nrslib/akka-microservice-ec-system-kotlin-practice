package com.example.shop.stock.service.app.service.stock

import akka.actor.typed.javadsl.ActorContext

class StockApplicationService(context: ActorContext<*>) {
    fun secureInventory(orderId: String, itemId: String) {
        println("secured")
    }

    fun cancelSecure(orderId: String) {

    }
}