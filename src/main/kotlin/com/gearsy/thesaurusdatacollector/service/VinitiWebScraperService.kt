package com.gearsy.thesaurusdatacollector.service

import com.gearsy.thesaurusdatacollector.model.VinitiRubricatorNode
import com.gearsy.thesaurusdatacollector.utils.ExternalApiProperties
import io.github.bonigarcia.wdm.WebDriverManager
import jakarta.annotation.PreDestroy
import org.openqa.selenium.By
import org.openqa.selenium.NoSuchElementException
import org.openqa.selenium.WebElement
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.support.ui.ExpectedConditions
import org.openqa.selenium.support.ui.WebDriverWait
import org.springframework.stereotype.Service
import java.time.Duration

@Service
class VinitiWebScraperService(
    private val externalApiProperties: ExternalApiProperties
) {
    // Инициализация драйвера
    private val driver = ChromeDriver()
    private val wait = WebDriverWait(driver, Duration.ofSeconds(10))

    fun scrapeWholeRubricTree(): List<VinitiRubricatorNode> {

        // Инициализация веб-драйвера
        WebDriverManager.chromedriver().setup()

        // Ожидание загрузки начальной страницы
        driver.get(externalApiProperties.cscsti.rubricator.url)

        // Список корневых рубрик рубрикатора ВИНИТИ
        val rootRubricList: MutableList<VinitiRubricatorNode> = mutableListOf()

        // TODO обернуть в try
        getRubricHierarchy()

        return rootRubricList
    }

    @PreDestroy
    fun cleanup() {
        println("Закрываем WebDriver...")
        driver.quit()

        // Завершаем процессы Chrome и ChromeDriver
        Runtime.getRuntime().exec("taskkill /F /IM chromedriver.exe /T")
    }


    fun getRubricHierarchy() {

        // Ожидание загрузки фрейма с иерархией
        wait.until(ExpectedConditions.frameToBeAvailableAndSwitchToIt(By.id("fraTree")))

        // Тег со списком корневых рубрик
        val rubricListTag = driver.findElement(By.id("ulRoot"))

        // Список корневых рубрик
        val upperLevelRubricTagList = rubricListTag.findElements(By.tagName("li"))

        // Рекурсивный обход все корневых рубрик
        for (upperRubricTag in upperLevelRubricTagList) {
            parseRubricRecursively(upperRubricTag)
        }
    }

    fun parseRubricRecursively(rubricTag: WebElement) {

        // Ссылка для раскрытия описания рубрики
        val expandRubricDescriptionTag = rubricTag.findElement(By.xpath(".//a[contains(@href, 'SelByCod')]"))

        // Тег, содержащий имя и код рубрики
        val rubricCodeNameTag = expandRubricDescriptionTag.findElement(By.className("notsel"))
        val rubricCodeName = rubricCodeNameTag.text
        println("rubricCodeName: $rubricCodeName")

        // Ссылка для раскрытия дочерних рубрик текущей рубрики
        val expandRubricChildRubricsTag = rubricTag.findElement(By.xpath(".//a[contains(@href, 'NodeIconClick')]"))
        expandRubricChildRubricsTag.click()

        // Проверка наличия вложенных рубрик
        try {
            // Тег, содержащий список дочерних рубрик
            val childRubricContainerTag = rubricTag.findElement(By.tagName("ul"))

            // Ожидание загрузки дочерних рубрик
            wait.until { childRubricContainerTag.findElements(By.tagName("li")).isNotEmpty() }

            // Список дочерних рубрик
            val childRubricTagList = childRubricContainerTag.findElements(By.tagName("li"))

            for (childRubricTag in childRubricTagList) {
                parseRubricRecursively(childRubricTag)
            }
        }
        catch (_: NoSuchElementException) {
        }
    }
}