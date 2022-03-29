package com.example.shop.stock.api.stock.commands

sealed interface StockServiceCommand {
    val entityId: String
}

data class SecureInventory(override val entityId: String, val itemId: String) : StockServiceCommand

data class CancelSecure(override val entityId: String, val itemId: String) : StockServiceCommand
