package com.example.kafka.serialization

data class MessageEnvelope(val type: Class<*>, val json: String)
