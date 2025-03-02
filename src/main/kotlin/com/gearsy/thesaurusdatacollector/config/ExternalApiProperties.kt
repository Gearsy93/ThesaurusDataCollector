package com.gearsy.thesaurusdatacollector.config

import com.gearsy.thesaurusdatacollector.config.externalApi.CscstiProperties
import com.gearsy.thesaurusdatacollector.config.externalApi.VinitiProperties
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "external-api")
class ExternalApiProperties {
    lateinit var cscsti: CscstiProperties
    lateinit var viniti: VinitiProperties
}






