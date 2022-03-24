package com.example.shop.order.api.order.replies

sealed interface OrderCreateSagaReply {
    val orderId: String
}

data class SecureInventoryReply(override val orderId: String, val success: Boolean) : OrderCreateSagaReply

data class ApproveBillingReply(override val orderId: String, val success: Boolean, val billingId: String) :
    OrderCreateSagaReply

data class CancelSecureReply(override val orderId: String, val success: Boolean) : OrderCreateSagaReply