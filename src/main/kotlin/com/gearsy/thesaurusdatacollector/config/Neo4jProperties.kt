package com.gearsy.thesaurusdatacollector.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "db.neo4j")
class Neo4jProperties {
    lateinit var uri: String
    lateinit var username: String
    lateinit var password: String
}