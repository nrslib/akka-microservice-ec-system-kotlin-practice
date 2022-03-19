package com.example.shop.order.api.order

object OrderServiceChannels {
    val commandChannel = "order-service-command-channel"
    val createOrderSagaReplyChannel = "order-service-create-order-saga-reply-channel"
}