package com.example.kafka.serialization

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule

object Serialization {
    val mapper: ObjectMapper = jacksonObjectMapper().registerKotlinModule()
}