package com.example.shop.billing.api.billing.commands

sealed interface BillingServiceCommand {
    val entityId: String
}

data class ApproveOrder(override val entityId: String, val consumerId: String) : BillingServiceCommand
