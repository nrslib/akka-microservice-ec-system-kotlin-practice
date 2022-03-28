package com.example.shop.order.api.order.replies

sealed interface OrderCreateSagaReply {
    val orderId: String
}

sealed interface SecureInventoryReply : OrderCreateSagaReply
data class SecureInventorySucceeded(override val orderId: String) : SecureInventoryReply
data class SecureInventoryFailed(override val orderId: String) : SecureInventoryReply

sealed interface ApproveBillingReply : OrderCreateSagaReply
data class ApproveBillingReplySucceeded(override val orderId: String, val billingId: String) :
    ApproveBillingReply
data class ApproveBillingReplyFailed(override val orderId: String) : ApproveBillingReply

sealed interface ApproveBillingResult : OrderCreateSagaReply
data class ApproveBillingCompleted(override val orderId: String, val billingId: String): ApproveBillingResult

data class CancelSecureReply(override val orderId: String, val success: Boolean) : OrderCreateSagaReply