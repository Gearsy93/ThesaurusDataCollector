package com.gearsy.thesaurusdatacollector.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.gearsy.thesaurusdatacollector.model.VinitiRubricatorNode
import com.gearsy.thesaurusdatacollector.config.ExternalApiProperties
import io.github.bonigarcia.wdm.WebDriverManager
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
import java.util.concurrent.TimeoutException

@Service
class VinitiWebScraperService(
    private val externalApiProperties: ExternalApiProperties
) {

    // Инициализация драйвера
    private lateinit var driver: ChromeDriver
    private lateinit var wait: WebDriverWait
    private val currentRubricator = "viniti"

    fun scrapeRubricFromTree(rubricCipher: String) {

        // Инициализация веб-драйвера
        driver = ChromeDriver()
        wait = WebDriverWait(driver, Duration.ofSeconds(10))
        WebDriverManager.chromedriver().setup()


        if (currentRubricator == "cscsti") {
            driver.get(externalApiProperties.cscsti.rubricator.url)
        }
        if (currentRubricator == "viniti") {
            driver.get(externalApiProperties.viniti.rubricator.url)
        }

        // Основной методы работы со страницей
        getRubricHierarchy(rubricCipher)
    }

    @PreDestroy
    fun cleanup() {
        driver.quit()

        // Завершаем процессы Chrome и ChromeDriver
        Runtime.getRuntime().exec("taskkill /F /IM chromedriver.exe /T")
    }


    fun getRubricHierarchy(rubricCipher: String) {

        // Ожидание загрузки фрейма с иерархией
        wait.until(ExpectedConditions.frameToBeAvailableAndSwitchToIt(By.id("fraTree")))

        // Тег со списком корневых рубрик
        val rubricListTag = driver.findElement(By.id("ulRoot"))

        // Список корневых рубрик для получения количества корневых рубрик
        val upperLevelRubricTagList = rubricListTag.findElements(By.className("closed"))

        // Маппер объектов для сериализации
        val objectMapper = jacksonObjectMapper()

        // Поиск тега необходимой рубрики
        val upperRubricTag = upperLevelRubricTagList.firstOrNull { it.getAttribute("id") == rubricCipher }

        if (upperRubricTag != null) {
            // Результат чтения дочерних рубрик
            val vinitiRubricatorNode = parseRubricRecursively(upperRubricTag)

            // Сериализация объекта
            val vinitiRubricatorNodeJson = objectMapper.writeValueAsString(vinitiRubricatorNode)

            // Сохранение корневой рубрики в файл
            val filePath = "src/main/resources/output/viniti/cscsti/${vinitiRubricatorNode?.cipher}.json"
            File(filePath).writeText(vinitiRubricatorNodeJson)

            // Обновление страницы для уменьшения шанса падения парсера
            driver.navigate().refresh()
            wait.until(ExpectedConditions.frameToBeAvailableAndSwitchToIt(By.id("fraTree")))
        } else {
            println("Рубрика с id=$rubricCipher не найдена")
        }
    }

    fun parseRubricRecursively(rubricTag: WebElement): VinitiRubricatorNode? {

        // Ссылка для раскрытия описания рубрики
        val expandRubricDescriptionTag = rubricTag.findElement(By.xpath(".//a[contains(@href, 'SelByCod')]"))
        println("Найден SelByCod")
        println(expandRubricDescriptionTag.text)

        // Ссылка для раскрытия дочерних рубрик текущей рубрики
        val expandRubricChildRubricsTag = rubricTag.findElement(By.xpath(".//a[contains(@href, 'NodeIconClick')]"))
        println("Найден NodeIconClick")
        println(expandRubricChildRubricsTag.text)

        // Ожидание кликабельности ссылки разворачивания рубрики
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

        // Главная форма с описаниями рубрики
        val nodeViewFormTag = driver.findElement(By.name("Form1"))
        println("Найден Form1")

        // Чтение содержимого и дочерних рубрик
        val vinitiRubricatorNode = parseRubricDescription(nodeViewFormTag)

        // Вернуться на фрейм списка
        driver.switchTo().defaultContent()
        println("Переключено на главный фрейм")
        wait.until(ExpectedConditions.frameToBeAvailableAndSwitchToIt(By.id("fraTree")))
        println("Переключено на fraTree")

        if (vinitiRubricatorNode == null) {
            return null
        }

        // Проверка наличия вложенных рубрик
        try {
            // Тег, содержащий список дочерних рубрик
            val childRubricContainerTag = rubricTag.findElement(By.className("clsShown"))
            println("Найден ul clsShown")

            // Ожидание загрузки дочерних рубрик
            try {
                wait.until { childRubricContainerTag.findElements(By.tagName("li")).isNotEmpty() }
            }
            catch (e: Exception) {
                wait.until { childRubricContainerTag.findElements(By.tagName("li")).isNotEmpty() }
            }
            println("Загружены li")

            // Список дочерних рубрик
            val childRubricTagList = childRubricContainerTag.findElements(By.xpath("./li"))
            println("Найдены li")

            for (childRubricTag in childRubricTagList) {
                val childNode = parseRubricRecursively(childRubricTag)
                if (childNode != null) {
                    vinitiRubricatorNode.addChildNode(childNode)
                }
            }
        } catch (_: NoSuchElementException) { println("Упало на поиске вложенных рубрик") }

        // Проверка наличия в текущей рубрике ссылок на другие рубрики
        try {
            // Чтение содержимого тега со списком ссылок на рубрики.//a[@title='Постоянная ссылка на данную рубрику']
            val linkCipherListTag = rubricTag.findElement(By.xpath("./*[@class='LinksList']"))
            vinitiRubricatorNode.linkCipherList = readLinkCipherListTag(linkCipherListTag)
        } catch (_: NoSuchElementException) {
            vinitiRubricatorNode.linkCipherList = listOf()
        }
        catch (_: TimeoutException) { }

        wait.until(ExpectedConditions.elementToBeClickable(expandRubricChildRubricsTag)).click()
        println("Закрыт раскрытый список")

        return vinitiRubricatorNode
    }

    fun readLinkCipherListTag(linkCipherListTag: WebElement): List<String> {

        // Ожидание загрузки дочерних рубрик
        try {
            wait.until { linkCipherListTag.findElements(By.tagName("li")).isNotEmpty() }
        }
        catch (e: Exception) {
            wait.until { linkCipherListTag.findElements(By.tagName("li")).isNotEmpty() }
        }
        println("Загружены li linkList")

        // Список дочерних рубрик
        val linkRubricTagList = linkCipherListTag.findElements(By.tagName("li"))
        println("Найдены li linkList")

        // Список шифров
        val linkCipherList = mutableListOf<String>()

        for (linkTag in linkRubricTagList) {

            // Поиск тега ссылки "a"
            try {
                wait.until { linkTag.findElements(By.tagName("a")).isNotEmpty() }
            }
            catch (e: Exception) {
                wait.until { linkTag.findElements(By.tagName("a")).isNotEmpty() }
            }

            val linkHrefTag = linkTag.findElement(By.tagName("a"))

            // Поиск тега font для проверки типа ссылки
            try {
                wait.until { linkTag.findElements(By.tagName("font")).isNotEmpty() }
            }
            catch (e: Exception) {
                wait.until { linkTag.findElements(By.tagName("font")).isNotEmpty() }
            }

            val fontTag = linkHrefTag.findElement(By.tagName("font"))

            // Содержимое font
            val fontContent = fontTag.getAttribute("innerHTML")

            // Если содержимое не "см. также ", пропускаем ссылку
            if (fontContent != null) {
                if (fontContent.trim() != "см. также") {
                    continue
                }
            }

            // Содержимое ссылки
            val linkHrefTagContent = linkHrefTag.getAttribute("href")

            // Извлечение номера рубрики
            val inner = linkHrefTagContent?.substringAfter("go_Link(")?.substringBefore(")")
            val linkRubricCipher = inner?.trim()?.trim('"')

            // Обновление списка ссылок
            if (linkRubricCipher != null) {
                linkCipherList.add(linkRubricCipher)
            }
        }

        return linkCipherList
    }

    fun parseRubricDescription(formTag: WebElement): VinitiRubricatorNode? {

        // Наименование рубрики
        var rubricNameValueTags = WebDriverWait(driver, Duration.ofSeconds(30))
            .until(ExpectedConditions.presenceOfAllElementsLocatedBy(By.xpath(".//a[@title='Постоянная ссылка на данную рубрику']")))

        // Если 2 тег не нашелся
        while (rubricNameValueTags.size == 1) {
            println(rubricNameValueTags[0].text)
            rubricNameValueTags = WebDriverWait(driver, Duration.ofSeconds(30))
                .until(ExpectedConditions.presenceOfAllElementsLocatedBy(By.xpath(".//a[@title='Постоянная ссылка на данную рубрику']")))
        }

        // Наименование рубрики
        val rubricName = rubricNameValueTags[1].text
        println("Рубрика: $rubricName")

        // Свойства рубрики
//        val rubricPropertiesTableTag = WebDriverWait(driver, Duration.ofSeconds(30))
//            .until(ExpectedConditions.presenceOfElementLocated(By.xpath(".//table[@class='scattered']")))
//        println("Свойства получены")

        // Все строки таблицы
        var tableRowTagList: List<WebElement>
        try {
            tableRowTagList = WebDriverWait(driver, Duration.ofSeconds(30))
                .until(ExpectedConditions.presenceOfAllElementsLocatedBy(By.xpath("//tr[.//td[contains(@class, 'NodeFieldName')]]")))
        }
        catch (e: Exception) {
            tableRowTagList = WebDriverWait(driver, Duration.ofSeconds(30))
                .until(ExpectedConditions.presenceOfAllElementsLocatedBy(By.xpath("//tr[.//td[contains(@class, 'NodeFieldName')]]")))
        }

        // Строка с шифром
        val cipherRowTag = tableRowTagList.first() { tableRowTag -> tableRowTag.findElement(By.className("NodeFieldName")).getAttribute("innerHTML") == "Ориг. шифр" }

        // Оригинальный шифр
        val originalCipher = cipherRowTag.findElement(By.className("NodeFieldValue")).getAttribute("innerHTML")!!
        println("Шифр")

        // Аспекты не рассматриваются в реализации
        if (originalCipher.lowercase() == "аспект") {
            return null
        }

        // Ссылка на окно с ключевыми словами
        val openKeywordsPopupLinkTag = WebDriverWait(driver, Duration.ofSeconds(30))
            .until(ExpectedConditions.elementToBeClickable(By.xpath(".//a[contains(@onclick, 'OpenKeywords')]")))
        println("Окно ключевые")

        // Извлечение ключевых слов из открываемого окна
        // mutableListOf<String>()
        val keywordList = processKeywordWindow(openKeywordsPopupLinkTag)

        // Таблица с отображениями рубрики на другие классификаторы (схемы)
//        val schemaMappingTableTag = WebDriverWait(driver, Duration.ofSeconds(30))
//            .until(ExpectedConditions.presenceOfElementLocated(By.xpath(".//table[@class='scattered']")))
//        println("Отображения на схемы")

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

        // Ожидание появления окна
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
            println("Нет ключевых слов")

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

        var tdTag = getKeywordPageTable()

        // Ссылки на страницы извлекаются после каждого обновления

        var linkTagList = tdTag.findElements(By.tagName("a"))

        // Список всех ключевых слов
        val keywordList = mutableListOf<String>()

        // Итерация по страницам
        var pageNumber: String = ""
        var offsetDone = false

        var pageIndex = -1
        do {

            // Ожидание подгрузки таблицы
            val keywordTableTag = wait.until(ExpectedConditions.presenceOfElementLocated(By.className("Grid")))
            println("Найдена таблица ключевых")

            // Список всех строк ключевых слов
            val keywordTagList = keywordTableTag.findElements(By.xpath(".//tr[contains(@class, 'GridItem') or contains(@class, 'GridAltItem')]"))
            println("Найдены строки ключевых")

            // Список ключевых слов
            keywordList.addAll(keywordTagList.map { keywordTag -> keywordTag.findElement(By.tagName("td")).text })
            println("Извлечен список ключевых")
            println("Все ключевые слова с новыми: $keywordList")

            // Переход на следующую страницу
            pageIndex++

            // Проверка символа последней ссылки
            val lastLinkSymbol = if (linkTagList.size > 0) {
                 linkTagList[linkTagList.size - 1].getAttribute("innerHTML")!!
            }
            else {
               ""
            }


            // Если последняя страница, выходим из циклов
            if (pageIndex == linkTagList.size && lastLinkSymbol.trim() != "...") {
                break
            }

            // Если номер страницы по последней ссылки меньше текущей страницы, выходим из цикла
            if (lastLinkSymbol.trim() != "..." && offsetDone) {
                if (lastLinkSymbol.toInt() < pageNumber.toInt()) {
                    break
                }
            }

            wait.until(ExpectedConditions.elementToBeClickable(linkTagList[pageIndex])).click()

            // После обновления страницы теги заново ищутся
            tdTag = getKeywordPageTable()

            // Ожидание загрузки ссылок
            try {
                wait.until { tdTag.findElements(By.tagName("a")).isNotEmpty() }
            }
            catch (e: Exception) {
                wait.until { tdTag.findElements(By.tagName("a")).isNotEmpty() }
            }

            linkTagList = tdTag.findElements(By.tagName("a"))

            // Содержимое тега с номером страницы
            pageNumber = tdTag.findElement(By.tagName("span")).text
            println("Номер текущей страницы: $pageNumber")

            if (pageNumber == "10") {
                println()
            }

            val currentFirstPageSymbol = linkTagList[0].getAttribute("innerHTML")!!

            // Если многоточие на 1 странице, произошло смещение на новый набор
            if (currentFirstPageSymbol == "..." && !offsetDone) {
                pageIndex--
                offsetDone = true
            }

        } while (pageIndex < linkTagList.size)

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

    fun getKeywordPageTable(): WebElement {
        // Панель с перечислением страниц
        try {
            wait.until { driver.findElements(By.className("GridPager")).isNotEmpty() }
        }
        catch (e: Exception) {
            wait.until { driver.findElements(By.className("GridPager")).isNotEmpty() }
        }
        val gridPagerTag = driver.findElement(By.className("GridPager"))

        // Ячейка с перечислением страниц
        val tdTag = gridPagerTag.findElement(By.tagName("td"))

        return tdTag
    }
}