package com.gearsy.thesaurusdatacollector.utils

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "external-api")
class ExternalApiProperties {
    lateinit var cscsti: CscstiProperties
    lateinit var viniti: VinitiProperties
}

class CscstiProperties {
    lateinit var rubricator: RubricatorProperties
}

class VinitiProperties {
    lateinit var rubricator: RubricatorProperties
}

class RubricatorProperties {
    lateinit var url: String
}
