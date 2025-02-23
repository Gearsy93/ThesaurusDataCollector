package com.gearsy.thesaurusdatacollector.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.gearsy.thesaurusdatacollector.model.VinitiRubricatorNode
import com.gearsy.thesaurusdatacollector.utils.ExternalApiProperties
import io.github.bonigarcia.wdm.WebDriverManager
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.openqa.selenium.By
import org.openqa.selenium.NoSuchElementException
import org.openqa.selenium.WebElement
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.support.ui.ExpectedConditions
import org.openqa.selenium.support.ui.WebDriverWait
import org.springframework.stereotype.Service
import java.io.File
import java.time.Duration

@Service
class VinitiWebScraperService(
    private val externalApiProperties: ExternalApiProperties
) {

    // Инициализация драйвера
    private val driver = ChromeDriver()
    private val wait = WebDriverWait(driver, Duration.ofSeconds(10))

    @PostConstruct
    fun init() {
        scrapeWholeRubricTree()
    }

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
        driver.quit()

        // Завершаем процессы Chrome и ChromeDriver
        Runtime.getRuntime().exec("taskkill /F /IM chromedriver.exe /T")
    }


    fun getRubricHierarchy() {

        // Ожидание загрузки фрейма с иерархией
        wait.until(ExpectedConditions.frameToBeAvailableAndSwitchToIt(By.id("fraTree")))

        // Тег со списком корневых рубрик
        var rubricListTag = driver.findElement(By.id("ulRoot"))

        // Список корневых рубрик для получения количества корневых рубрик
        var upperLevelRubricTagList = rubricListTag.findElements(By.tagName("li"))
        val upperLevelRubricTagCount = upperLevelRubricTagList.size

        // Маппер объектов
        val objectMapper = jacksonObjectMapper()

        val startIndex = 3

        // upperLevelRubricTagCount - 1
        val endIndex = 3

        // Рекурсивный обход все корневых рубрик с перезагрузкой страницы
        for (i in startIndex..endIndex) {

            val upperRubricTag = upperLevelRubricTagList[i]

            val vinitiRubricatorNode = parseRubricRecursively(upperRubricTag)

            // Сериализация объекта
            val vinitiRubricatorNodeJson = objectMapper.writeValueAsString(vinitiRubricatorNode)

            // Сохранение корневой рубрики в файл
            val filePath = "src/main/resources/output/${vinitiRubricatorNode?.cipher}.json"
            File(filePath).writeText(vinitiRubricatorNodeJson)

            // Обновление страницы для уменьшения шанса падения парсера
            driver.navigate().refresh()
            wait.until(ExpectedConditions.frameToBeAvailableAndSwitchToIt(By.id("fraTree")))

            // Чтение иерархии заново
            rubricListTag = driver.findElement(By.id("ulRoot"))
            upperLevelRubricTagList = rubricListTag.findElements(By.tagName("li"))
        }

    }

    fun parseRubricRecursively(rubricTag: WebElement): VinitiRubricatorNode? {

        // Проверка является ли элемент списка ссылкой
        try {
            rubricTag.findElement(By.className("link"))
            return null
        } catch (_: NoSuchElementException) { }


        // Ссылка для раскрытия описания рубрики
        val expandRubricDescriptionTag = rubricTag.findElement(By.xpath(".//a[contains(@href, 'SelByCod')]"))
        println("Найден SelByCod")
        println(expandRubricDescriptionTag.text)

        val expandRubricChildRubricsTag = rubricTag.findElement(By.xpath(".//a[contains(@href, 'NodeIconClick')]"))
        println("Найден NodeIconClick")
        println(expandRubricChildRubricsTag.text)

        // Ссылка для раскрытия дочерних рубрик текущей рубрики
        wait.until(ExpectedConditions.elementToBeClickable(expandRubricChildRubricsTag)).click()
        println("Кликнуто на развернуть рубрики")

        // Переключение на фрейм описания рубрики
        driver.switchTo().defaultContent()
        println("Переключено на главный фрейм")
        wait.until(ExpectedConditions.frameToBeAvailableAndSwitchToIt(By.id("fraNode")))
        println("Переключено на fraNode")

        // Извлечение содержимого описания рубрики
        wait.until(ExpectedConditions.presenceOfElementLocated(By.name("Form1")))
        println("Появился Form1")
        val nodeViewFormTag = driver.findElement(By.name("Form1"))
        println("Найден Form1")
        val vinitiRubricatorNode = parseRubricDescription(nodeViewFormTag)

        // Вернуться на фрейм списка
        driver.switchTo().defaultContent()
        println("Переключено на главный фрейм")
        wait.until(ExpectedConditions.frameToBeAvailableAndSwitchToIt(By.id("fraTree")))
        println("Переключено на fraTree")

        // Проверка наличия вложенных рубрик
        try {
            // Тег, содержащий список дочерних рубрик
            val childRubricContainerTag = rubricTag.findElement(By.tagName("ul"))
            println("Найден ul")

            // Ожидание загрузки дочерних рубрик
            try {
                wait.until { childRubricContainerTag.findElements(By.tagName("li")).isNotEmpty() }
            }
            catch (e: Exception) {
                wait.until { childRubricContainerTag.findElements(By.tagName("li")).isNotEmpty() }
            }
            println("Загружены li")

            // Список дочерних рубрик
            val childRubricTagList = childRubricContainerTag.findElements(By.tagName("li"))
            println("Найдены li")

            for (childRubricTag in childRubricTagList) {
                val childNode = parseRubricRecursively(childRubricTag)
                if (childNode != null) {
                    vinitiRubricatorNode.addChildNode(childNode)
                }
            }
        } catch (_: NoSuchElementException) {
        }
        wait.until(ExpectedConditions.elementToBeClickable(expandRubricChildRubricsTag)).click()
        println("Закрыт раскрытый список")

        return vinitiRubricatorNode
    }

    fun parseRubricDescription(formTag: WebElement): VinitiRubricatorNode {

        // Наименование рубрики
        var rubricNameValueTags = WebDriverWait(driver, Duration.ofSeconds(30))
            .until(ExpectedConditions.presenceOfAllElementsLocatedBy(By.xpath(".//a[@title='Постоянная ссылка на данную рубрику']")))

        // Если 2 тег не нашелся
        while (rubricNameValueTags.size == 1) {
            println(rubricNameValueTags[0].text)
            rubricNameValueTags = WebDriverWait(driver, Duration.ofSeconds(30))
                .until(ExpectedConditions.presenceOfAllElementsLocatedBy(By.xpath(".//a[@title='Постоянная ссылка на данную рубрику']")))
        }

        val rubricName = rubricNameValueTags[0].text
        println("Рубрика: $rubricName")

        // Свойства рубрики
        val rubricPropertiesTableTag = WebDriverWait(driver, Duration.ofSeconds(30))
            .until(ExpectedConditions.presenceOfElementLocated(By.xpath(".//table[@class='scattered']")))

        println("Свойства")

        // Оригинальный шифр
        val originalCipherValueTag = WebDriverWait(driver, Duration.ofSeconds(30))
            .until(ExpectedConditions.presenceOfElementLocated(By.className("NodeFieldValue")))
        val originalCipher = originalCipherValueTag.text
        println("Шифр")

        // Ссылка на окно с ключевыми словами
        val openKeywordsPopupLinkTag = WebDriverWait(driver, Duration.ofSeconds(30))
            .until(ExpectedConditions.elementToBeClickable(By.xpath(".//a[contains(@onclick, 'OpenKeywords')]")))
        println("Окно ключевые")

        // Извлечение ключевых слов из открываемого окна
        val keywordList = processKeywordWindow(openKeywordsPopupLinkTag)

        // Таблица с отображениями рубрики на другие классификаторы (схемы)
        val schemaMappingTableTag = WebDriverWait(driver, Duration.ofSeconds(30))
            .until(ExpectedConditions.presenceOfElementLocated(By.xpath(".//table[@class='scattered']")))
        println("Отображения на схемы")

        return VinitiRubricatorNode(
            originalCipher,
            rubricName,
            keywordList,
            null
        )
    }

    fun processKeywordWindow(openKeywordsPopupLinkTag: WebElement): List<String>? {

        // Хендлер текущего окна
        val mainWindow = driver.windowHandle
        println("Текущее окно")

        // Открыть новое окно и дождаться появления
        wait.until(ExpectedConditions.elementToBeClickable(openKeywordsPopupLinkTag)).click()
        println("Окно со словами")

        try {
            wait.until { driver.windowHandles.size > 1 }
        }
        catch (e: Exception) {
            wait.until(ExpectedConditions.elementToBeClickable(openKeywordsPopupLinkTag)).click()

            wait.until { driver.windowHandles.size > 1 }
        }
        println("Появилось окно")

        // Переключиться на новое окно
        val newWindow = driver.windowHandles.find { it != mainWindow }
        println("Нашлось окно")
        requireNotNull(newWindow) { "Новое окно не появилось" }
        newWindow.let { driver.switchTo().window(it) }
        println("Переключилось на окно")

        // Если нет ключевых слов, выйти
        try {
            driver.findElement(By.xpath(".//span[contains(text(),'Ключевых слов')]"))
            println("Нет ключевых, уходи")
            // Закрыть открытое окно и вернуться к основному
            driver.close()
            println("Закрыто окно")
            driver.switchTo().window(mainWindow)
            println("Переключено на главное окно")
            driver.switchTo().defaultContent()
            println("Переключен на главный фрейм")
            wait.until(ExpectedConditions.frameToBeAvailableAndSwitchToIt(By.id("fraNode")))
            println("Переключен на fraNode")
            return null
        }
        catch (_: NoSuchElementException) {}

        // Тег таблицы ключевых слов
        val keywordTableTag = driver.findElement(By.className("Grid"))
        println("Найдена таблица ключевых")

        // Список всех строк ключевых слов
        // TODO добавить try
        val keywordTagList = keywordTableTag.findElements(By.xpath(".//tr[contains(@class, 'GridItem') or contains(@class, 'GridAltItem')]"))
        println("Найдены строки ключевых")

        // Список ключевых слов
        val keywordList = keywordTagList.map { keywordTag -> keywordTag.findElement(By.tagName("td")).text }
        println("Извлечен список ключевых")

        println("Ключевые: $keywordList")

        // Закрыть открытое окно и вернуться к основному
        driver.close()
        println("Закрыто окно")
        driver.switchTo().window(mainWindow)
        println("Переключено на главное окно")
        driver.switchTo().defaultContent()
        println("Переключен на главный фрейм")
        wait.until(ExpectedConditions.frameToBeAvailableAndSwitchToIt(By.id("fraNode")))
        println("Переключен на fraNode")

        return keywordList
    }
}