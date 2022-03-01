package com.example.shop.order.service.app.provider

import akka.actor.typed.Behavior
import com.example.shop.order.service.app.service.order.OrderService
import com.example.shop.shared.id.IdGenerator

class OrderServiceProvider(private val idGenerator: IdGenerator) {
    fun provide(): Behavior<OrderService.Message> = OrderService.create(idGenerator)
}