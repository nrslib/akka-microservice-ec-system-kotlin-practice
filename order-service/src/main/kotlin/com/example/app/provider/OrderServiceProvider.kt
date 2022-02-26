package com.example.app.provider

import akka.actor.typed.Behavior
import com.example.app.service.order.OrderService
import com.example.shared.id.IdGenerator

class OrderServiceProvider(private val idGenerator: IdGenerator) {
    fun provide(): Behavior<OrderService.Message> = OrderService.create(idGenerator)
}