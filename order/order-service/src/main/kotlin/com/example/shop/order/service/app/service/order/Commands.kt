package com.example.shop.order.service.app.service.order

data class OrderCreateResult(val orderId: String, val success: Boolean)

data class ItemIdAndNr(val id: String, val nr: Int)