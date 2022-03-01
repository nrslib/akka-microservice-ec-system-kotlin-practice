package com.example.shop.shared.id

import java.util.*

class UuidIdGenerator : IdGenerator {
    override fun generate(): String = UUID.randomUUID().toString()
}