package com.example.shop.shared.id

class FixedIdGenerator(private val id: String) : IdGenerator {
    override fun generate(): String = id
}