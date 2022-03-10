package com.example.kafka.serialization

import org.apache.kafka.common.serialization.Serializer


class PayloadSerializer : Serializer<Any> {
    override fun serialize(topic: String, data: Any): ByteArray {
        val json = Serialization.mapper.writeValueAsString(data)
        val t = data::class.java
        val envelope = MessageEnvelope(t, json)
        val message = Serialization.mapper.writeValueAsString(envelope)

        return message.toByteArray()
    }
}