package com.gearsy.thesaurusdatacollector.config.properties

import com.gearsy.thesaurusdatacollector.config.properties.externalApi.CscstiProperties
import com.gearsy.thesaurusdatacollector.config.properties.externalApi.VinitiProperties
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "external-api")
class ExternalApiProperties {
    lateinit var cscsti: CscstiProperties
    lateinit var viniti: VinitiProperties
}






