package com.example.shared.id

class FixedIdGenerator(private val id: String) : IdGenerator {
    override fun generate(): String = id
}