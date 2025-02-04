package com.gearsy.thesaurusdatacollector.service

import com.gearsy.thesaurusdatacollector.model.VinitiRubricatorNode
import io.github.bonigarcia.wdm.WebDriverManager
import org.openqa.selenium.WebDriver
import org.openqa.selenium.chrome.ChromeDriver
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Service
import java.time.Duration

@Service
@Configuration
@ConfigurationProperties(prefix = "external-api") // Связь с application.yml
class VinitiWebScraperService {

    @Value("\${external-api.viniti.rubricator.url}")
    private val vinitiRubricatorURL: String = ""

    fun scrapeWholeRubricTree(): List<VinitiRubricatorNode> {

        // Инициализация веб-драйвера
        WebDriverManager.chromedriver().setup()

        val driver = ChromeDriver()
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(15))
        driver.get(vinitiRubricatorURL)

        // Список корневых рубрик рубрикатора ВИНИТИ
        val rootRubricList: MutableList<VinitiRubricatorNode> = mutableListOf()


        return rootRubricList
    }

    fun scrapAspxPageData() {

    }
}