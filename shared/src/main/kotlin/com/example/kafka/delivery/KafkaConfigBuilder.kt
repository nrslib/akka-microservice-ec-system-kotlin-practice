package com.example.kafka.delivery

class KafkaConfigBuilder(private val bootstrapServers: String) {
    private val properties = mutableMapOf<String, String>()

    fun addProperty(key: String, value: String): KafkaConfigBuilder {
        properties[key] = value

        return this
    }

    fun addProperties(properties: Map<String, String>): KafkaConfigBuilder {
        this.properties.putAll(properties)

        return this
    }

    fun build(): KafkaConfig = KafkaConfig(bootstrapServers, properties)
}