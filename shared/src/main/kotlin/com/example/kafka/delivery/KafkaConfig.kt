package com.example.kafka.delivery

import com.typesafe.config.Config

data class KafkaConfig(val bootstrapServers: String, val properties: Map<String, String>) {
    companion object {
        fun load(config: Config): KafkaConfig {
            val bootstrapServers = config.getString("kafka.bootstrap-servers")
            val properties = mutableMapOf<String, String>()

            if (config.hasPath("kafka.properties")) {
                val propertyConfig = config.getConfig("kafka.properties")
                addPropertyIfExists(propertyConfig, "security.protocol", properties)
                addPropertyIfExists(propertyConfig, "sasl.mechanism", properties)
                addPropertyIfExists(propertyConfig, "sasl.jaas.config", properties)
                addPropertyIfExists(propertyConfig, "sasl.client.callback.handler.class", properties)
            }

            return KafkaConfigBuilder(bootstrapServers)
                .addProperties(properties)
                .build()
        }

        private fun addPropertyIfExists(config: Config, propertyPath: String, properties: MutableMap<String, String>) {
            if (config.hasPath(propertyPath)) {
                properties[propertyPath] = config.getString(propertyPath)
            }
        }
    }
}