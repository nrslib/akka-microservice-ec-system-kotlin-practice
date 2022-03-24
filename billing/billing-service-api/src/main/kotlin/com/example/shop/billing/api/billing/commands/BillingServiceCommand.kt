package com.example.shop.billing.api.billing.commands

sealed interface BillingServiceCommand

data class ApproveOrder(val orderId: String, val consumerId: String) : BillingServiceCommand
