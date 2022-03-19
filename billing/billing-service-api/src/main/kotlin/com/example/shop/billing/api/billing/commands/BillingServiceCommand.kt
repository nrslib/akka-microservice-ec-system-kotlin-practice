package com.example.shop.billing.api.billing.commands

sealed interface BillingServiceCommand
sealed interface BillingServiceReply

data class ApproveOrder(val orderId: String) : BillingServiceCommand

data class ApproveOrderReply(val orderId: String, val success: Boolean) : BillingServiceReply