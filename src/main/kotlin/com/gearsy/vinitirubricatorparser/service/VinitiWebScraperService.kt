package com.gearsy.vinitirubricatorparser.service

import com.gearsy.vinitirubricatorparser.model.VinitiRubricatorNode
import io.github.bonigarcia.wdm.WebDriverManager
import jakarta.annotation.PostConstruct
import org.openqa.selenium.WebDriver
import org.openqa.selenium.chrome.ChromeDriver
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component
import java.time.Duration

@Component
@Configuration
@ConfigurationProperties(prefix = "external-api") // Связь с application.yml
class VinitiWebScraperService {

    @Value("\${external-api.viniti.rubricator.url}")
    private val vinitiRubricatorURL: String = ""

    private final val driver: WebDriver

    init {
        WebDriverManager.chromedriver().setup()
        driver = ChromeDriver()
    }

    @PostConstruct
    fun startScrapingService() {

        // Инициализация веб-драйвера
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(15));
        driver.get(vinitiRubricatorURL)

        // Список корневых рубрик рубрикатора ВИНИТИ
        val rootRubricList: MutableList<VinitiRubricatorNode> = mutableListOf()



    }

    fun scrapAspxPageData() {

    }
}