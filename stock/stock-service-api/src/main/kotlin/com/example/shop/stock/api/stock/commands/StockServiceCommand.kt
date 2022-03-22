package com.example.shop.stock.api.stock.commands

sealed interface StockServiceCommand

data class SecureInventory(val orderId: String, val itemId: String) : StockServiceCommand

data class CancelSecure(val orderId: String, val itemId: String) : StockServiceCommand
