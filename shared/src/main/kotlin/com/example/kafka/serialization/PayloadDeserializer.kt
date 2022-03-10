package com.example.kafka.serialization

import org.apache.kafka.common.serialization.Deserializer

class PayloadDeserializer<T>() : Deserializer<T> {
    override fun deserialize(topic: String, data: ByteArray): T {
        val envelope = Serialization.mapper.readValue(data, MessageEnvelope::class.java)
        val instance = Serialization.mapper.readValue(envelope.json, envelope.type)

        return instance as T
    }
}