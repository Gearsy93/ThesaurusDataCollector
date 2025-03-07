package com.gearsy.thesaurusdatacollector.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.gearsy.thesaurusdatacollector.model.CSCSTIRubricatorNode
import com.gearsy.thesaurusdatacollector.config.properties.ExternalApiProperties
import com.gearsy.thesaurusdatacollector.model.AbstractRubricatorNode
import com.gearsy.thesaurusdatacollector.model.VinitiRubricatorNode
import io.github.bonigarcia.wdm.WebDriverManager
import jakarta.annotation.PreDestroy
import org.openqa.selenium.By
import org.openqa.selenium.ElementClickInterceptedException
import org.openqa.selenium.NoSuchElementException
import org.openqa.selenium.WebElement
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.support.ui.ExpectedConditions
import org.openqa.selenium.support.ui.FluentWait
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
        wait = WebDriverWait(driver, Duration.ofSeconds(6))
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
            val filePath = "src/main/resources/output/rubricator/${currentRubricator}/${vinitiRubricatorNode?.cipher}.json"
            File(filePath).writeText(vinitiRubricatorNodeJson)

            // Обновление страницы для уменьшения шанса падения парсера
            driver.navigate().refresh()
            wait.until(ExpectedConditions.frameToBeAvailableAndSwitchToIt(By.id("fraTree")))
        } else {
            println("Рубрика с id=$rubricCipher не найдена")
        }
    }

    fun parseRubricRecursively(rubricTag: WebElement): AbstractRubricatorNode? {

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
        var attempt = 0
        val maxAttempts = 3
        var vinitiRubricatorNode: AbstractRubricatorNode? = null
        while (attempt < maxAttempts) {
            try {
                attempt++
                vinitiRubricatorNode = parseRubricDescription(nodeViewFormTag)
            }
            catch (e: Exception) {
                println("\nОшибка при чтении описания рубрики, вторая попытка\n")

                // Закрываем все открытые окна, кроме главного
                val mainWindow = driver.windowHandles.first() // Запоминаем главное окно
                driver.windowHandles
                    .filter { it != mainWindow } // Закрываем только не главное окно
                    .forEach { window ->
                        driver.switchTo().window(window).close()
                    }

                // Переключаемся обратно на fraNode
                driver.switchTo().defaultContent()
                wait.until(ExpectedConditions.frameToBeAvailableAndSwitchToIt(By.id("fraNode")))

                if (attempt >= maxAttempts) {
                    println("Не удалось успешно обработать рубрику после $maxAttempts попыток.")
                    throw Exception()
                }
            }
        }


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

            // Вложение рубрик через <i>
            try {
                val iContainerTag = childRubricContainerTag.findElement(By.xpath("./i"))
                val iContainerChildTagList = iContainerTag.findElements(By.xpath("./li"))
                childRubricTagList.addAll(iContainerChildTagList)
            }
            catch (_: NoSuchElementException) { }

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

        try {
            wait.until(ExpectedConditions.elementToBeClickable(expandRubricChildRubricsTag)).click()
        }
        catch (e: Exception) {
            wait.until(ExpectedConditions.elementToBeClickable(expandRubricChildRubricsTag)).click()
        }
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

    fun parseRubricDescription(formTag: WebElement): AbstractRubricatorNode? {

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

        // Все строки таблицы
        var tableRowTagList: List<WebElement>
        try {
            tableRowTagList = WebDriverWait(driver, Duration.ofSeconds(40))
                .until(ExpectedConditions.presenceOfAllElementsLocatedBy(By.xpath("//tr[.//td[contains(@class, 'NodeFieldName')]]")))
        }
        catch (e: Exception) {
            try {
                tableRowTagList = WebDriverWait(driver, Duration.ofSeconds(40))
                    .until(ExpectedConditions.presenceOfAllElementsLocatedBy(By.xpath("//tr[.//td[contains(@class, 'NodeFieldName')]]")))
            }
            catch (e: Exception) {
                try {
                    tableRowTagList = WebDriverWait(driver, Duration.ofSeconds(40))
                        .until(ExpectedConditions.presenceOfAllElementsLocatedBy(By.xpath("//tr[.//td[contains(@class, 'NodeFieldName')]]")))
                }
                catch (e: Exception) {
                    return CSCSTIRubricatorNode(
                        "УПАЛО",
                        rubricName,
                        null
                    )
                }
            }
        }

        // Строка с шифром
        val cipherRowTag = tableRowTagList.first { tableRowTag -> tableRowTag.findElement(By.className("NodeFieldName")).getAttribute("innerHTML") == "Ориг. шифр" }

        // Оригинальный шифр
        val originalCipher = cipherRowTag.findElement(By.className("NodeFieldValue")).getAttribute("innerHTML")!!
        println("Шифр")

        // Аспекты не рассматриваются в реализации
        if (originalCipher.lowercase().contains("аспект")) {
            return null
        }

        // Ссылка на окно с ключевыми словами
        val openKeywordsPopupLinkTag = WebDriverWait(driver, Duration.ofSeconds(30))
            .until(ExpectedConditions.elementToBeClickable(By.xpath(".//a[contains(@onclick, 'OpenKeywords')]")))
        println("Окно ключевые")

        // Извлечение ключевых слов из открываемого окна
        val keywordList = processKeywordWindow(openKeywordsPopupLinkTag)


        if (currentRubricator == "cscsti") {
            return CSCSTIRubricatorNode(
                originalCipher,
                rubricName,
                keywordList
            )
        }
        else if (currentRubricator == "viniti") {

            // Таблица с отображениями рубрики на другие классификаторы (схемы)
            var schemaMappingTableTagList = WebDriverWait(driver, Duration.ofSeconds(3))
                .until(ExpectedConditions.presenceOfAllElementsLocatedBy(By.xpath(".//table[@class='scattered']")))
            println("Отображения на схемы")

            // Повторная попытка найти
            if (schemaMappingTableTagList.size != 2) {
                schemaMappingTableTagList = WebDriverWait(driver, Duration.ofSeconds(3))
                    .until(ExpectedConditions.presenceOfAllElementsLocatedBy(By.xpath(".//table[@class='scattered']")))
            }

            // Извлечение шифра рубрики ГРНТИ
            val cscstiRubricCipher = processMappingWindows(schemaMappingTableTagList[1])

            return VinitiRubricatorNode(
                originalCipher,
                rubricName,
                keywordList,
                vinitiParentNodeCipher=cscstiRubricCipher
            )
        }
        else {
            throw Exception("Рубрикатор не поддерживается")
        }
    }

    fun processMappingWindows(schemaMappingTableTag: WebElement): String {

        // Ожидание загрузки ссылок на рубрикаторы
        try {
            wait.until { schemaMappingTableTag.findElements(By.xpath(".//a[contains(@href, 'OpenMappedNodes(\"RGNTI\")')]")).isNotEmpty() }
        }
        catch (e: Exception) {
            wait.until { schemaMappingTableTag.findElements(By.xpath(".//a[contains(@href, 'OpenMappedNodes(\"RGNTI\")')]")).isNotEmpty() }
        }

        // Тег со ссылкой на открытие окна с отображением на ГРНТИ
        val cscstiLinkTag = schemaMappingTableTag.findElement(By.xpath(".//a[contains(@href, 'OpenMappedNodes(\"RGNTI\")')]"))
        println("Ссылка на отображение ГРНТИ найдено")

        // Открытие нового окна
        val mainWindow = openWindow(cscstiLinkTag)
        println("Открыто окно с описанием отображения")

        // Проверка наличия тега-ссылки на рубрику
        try {
            val customWait = FluentWait(driver)
                .withTimeout(Duration.ofSeconds(1.5.toLong()))   // Максимальное время ожидания
                .pollingEvery(Duration.ofMillis(500)) // Интервал проверки наличия элемента
                .ignoring(org.openqa.selenium.NoSuchElementException::class.java) // Игнорируем ошибку отсутствия элемента

            val elements = customWait.until {
                driver.findElements(By.xpath(".//a[contains(@onclick, 'SetWinLocation')]"))
            }.filterNotNull()

            if (elements.isNotEmpty()) {
                println("Элементы найдены")
            } else {
                println("Элементы не найдены")
                closeWindow(mainWindow)
                return ""
            }
        }
        catch (e: Exception) {

            // Отображения нет, закрытие текущего окна, переключение на основное (fraNode)
            closeWindow(mainWindow)
            return ""
        }

        // Получение тега-ссылки
        val rubricLinkTag = driver.findElement(By.xpath(".//a[contains(@onclick, 'SetWinLocation')]"))

        // Извлечение содержимого из тега
        val linkInner = rubricLinkTag.getAttribute("innerHTML")

        // Извлечение шифра тега
        val cscstiRubricCipher = linkInner!!.split(" ")[0]

        // Закрытие текущего окна, переключение на основное (fraNode)
        closeWindow(mainWindow)

        return cscstiRubricCipher
    }

    fun processKeywordWindow(openKeywordsPopupLinkTag: WebElement): List<String>? {

        // Открытие нового окна
        val mainWindow = openWindow(openKeywordsPopupLinkTag)

        // Если нет ключевых слов, выйти
        try {
            driver.findElement(By.xpath(".//span[contains(text(),'Ключевых слов')]"))
            println("Нет ключевых слов")

            // Закрытие текущего окна, переключение на основное (fraNode)
            closeWindow(mainWindow)

            return null
        }
        catch (_: NoSuchElementException) {}

        // Список всех ключевых слов
        val keywordList = mutableListOf<String>()

        // Номер текущей страницы
        var currentPageNumber: String

        do {

            // Ожидание загрузки таблицы
            val tdTag = getKeywordPageTable()

            // Ожидание подгрузки таблицы
            val keywordTableTag = wait.until(ExpectedConditions.presenceOfElementLocated(By.className("Grid")))
            println("Найдена таблица ключевых")

            // Список всех строк ключевых слов
            val keywordTagList = keywordTableTag.findElements(By.xpath(".//tr[contains(@class, 'GridItem') or contains(@class, 'GridAltItem')]"))
            println("Найдены строки ключевых")

            // Список ключевых слов
            keywordList.addAll(keywordTagList.map { keywordTag -> keywordTag.findElement(By.tagName("td")).text })
            println("Все ключевые слова с новыми: $keywordList")

            // Ожидание загрузки ссылок
            try {
                wait.until { tdTag.findElements(By.tagName("a")).isNotEmpty() }
            }
            catch (e: Exception) {
                wait.until { tdTag.findElements(By.tagName("a")).isNotEmpty() }
            }

            // Ссылки на страницы
            val linkTagList = tdTag.findElements(By.tagName("a"))

            // Содержимое тега с номером страницы
            currentPageNumber = tdTag.findElement(By.tagName("span")).text
            println("Номер текущей страницы: $currentPageNumber")

            // Обновление состояния страницы
            if (updateKeywordPageState(linkTagList, currentPageNumber)) {
                break
            }

        } while (true)


        // Закрытие текущего окна, переключение на основное (fraNode)
        closeWindow(mainWindow)

        return keywordList
    }

    fun updateKeywordPageState(linkTagList: List<WebElement>, currentPageNumber: String): Boolean {

        val linkTextList = linkTagList.map { it.getAttribute("innerHTML") }

        for ((i, linkText) in linkTextList.withIndex()) {

            // ... в начале списка не интересует
            if (i == 0 && linkText == "...") {
                continue
            }
            // Если это число
            else if (linkText != "...") {

                // Числовое представление
                val currentPageInt = currentPageNumber.toInt()
                val iteratePageInt = linkText!!.toInt()

                // Если номер страницы по ссылке больше текущей страницы, переходим на следующую страницу
                if (iteratePageInt > currentPageInt) {

                    // Переход на следующую страницу
                    wait.until(ExpectedConditions.elementToBeClickable(linkTagList[i])).click()
                    return false
                }

            }
            // Не число, значит переход на следующий набор страниц
            else {

                // Переход на следующую страницу
                wait.until(ExpectedConditions.elementToBeClickable(linkTagList[i])).click()
                return false
            }

        }

        return true
    }

    fun openWindow(clickableTag: WebElement): String {

        // Хендлер текущего окна
        val mainWindow = driver.windowHandle
        println("Текущее окно")

        val customWait = FluentWait(driver)
            .withTimeout(Duration.ofSeconds(10))  // Общий таймаут ожидания
            .pollingEvery(Duration.ofMillis(500)) // Частота проверок
            .ignoring(NoSuchElementException::class.java) // Игнорируем отсутствие элемента
            .ignoring(ElementClickInterceptedException::class.java) // Если элемент перекрыт другим
            .ignoring(TimeoutException::class.java) // Если элемент долго загружается

        try {
            val element = customWait.until {
                if (clickableTag.isDisplayed && clickableTag.isEnabled) {
                    clickableTag
                } else {
                    null // Ждем, пока элемент не станет видимым и активным
                }
            }
            element?.click()
            println("Окно открыто")
        } catch (e: Exception) {
            println("Окно не открылось, вторая попытка...")

            val element = customWait.until {
                if (clickableTag.isDisplayed && clickableTag.isEnabled) {
                    clickableTag
                } else {
                    null // Ждем, пока элемент не станет видимым и активным
                }
            }
            element?.click()
            println("Окно открыто")
        }

        // Ожидание появления окна
        try {
            wait.until { driver.windowHandles.size > 1 }
        }
        catch (e: Exception) {
            wait.until(ExpectedConditions.elementToBeClickable(clickableTag)).click()
            wait.until { driver.windowHandles.size > 1 }
        }
        println("Появилось окно")

        // Переключиться на новое окно
        val newWindow = driver.windowHandles.find { it != mainWindow }
        println("Нашлось окно")

        requireNotNull(newWindow) { "Новое окно не появилось" }
        newWindow.let { driver.switchTo().window(it) }
        println("Переключилось на окно")

        return mainWindow
    }

    fun closeWindow(mainWindow: String) {

        // Закрыть открытое окно и вернуться к основному
        driver.close()
        println("Закрыто окно")

        driver.switchTo().window(mainWindow)
        println("Переключено на главное окно")

        driver.switchTo().defaultContent()
        println("Переключен на главный фрейм")

        wait.until(ExpectedConditions.frameToBeAvailableAndSwitchToIt(By.id("fraNode")))
        println("Переключен на fraNode")
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