package com.gearsy.thesaurusdatacollector.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.gearsy.thesaurusdatacollector.model.CSCSTIRubricatorNode
import com.gearsy.thesaurusdatacollector.config.properties.ExternalApiProperties
import com.gearsy.thesaurusdatacollector.model.AbstractRubricatorNode
import com.gearsy.thesaurusdatacollector.model.VinitiRubricatorNode
import io.github.bonigarcia.wdm.WebDriverManager
import jakarta.annotation.PreDestroy
import org.openqa.selenium.*
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.support.ui.ExpectedConditions
import org.openqa.selenium.support.ui.FluentWait
import org.openqa.selenium.support.ui.WebDriverWait
import org.springframework.stereotype.Service
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.time.Duration

@Service
class VinitiWebScraperService(
    private val externalApiProperties: ExternalApiProperties
) {

    // Инициализация драйвера
    private lateinit var driver: ChromeDriver
    private lateinit var wait: WebDriverWait
    private val currentRubricator = "viniti"

    // Путь к файлу
    lateinit var filePathR: String

    // Открываем файл в режиме добавления (append=true) один раз в начале работы алгоритма
    lateinit var fileWriter: FileWriter
    lateinit var bufferedWriter: BufferedWriter
    lateinit var printWriter: PrintWriter

    var read = false
    var readAll = false

    // Маппер объектов для сериализации
    val objectMapper = jacksonObjectMapper()

    fun scrapeRubricFromTree(rubricCipher: String) {

        filePathR = "src/main/resources/output/rubricator/${currentRubricator}/${rubricCipher}_r.json"

        fileWriter = FileWriter(filePathR, true)
        bufferedWriter = BufferedWriter(fileWriter)
        printWriter = PrintWriter(bufferedWriter)

        // Инициализация веб-драйвера
        driver = ChromeDriver()
        wait = WebDriverWait(driver, Duration.ofSeconds(1))
        WebDriverManager.chromedriver().setup()


        if (currentRubricator == "cscsti") {
            driver.get(externalApiProperties.cscsti.rubricator.url)
        }
        if (currentRubricator == "viniti") {
            driver.get(externalApiProperties.viniti.rubricator.url)
        }

        try {
            // Основной методы работы со страницей
            getRubricHierarchy(rubricCipher)
        }
        catch (ex: Exception) {
            Runtime.getRuntime().addShutdownHook(Thread {

                writeToFileSafe("\n\n\n")
                printWriter.close()
            })
            throw ex
        }
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

// Определяем тип рубрики по `class`
        val rubricClass = rubricTag.getAttribute("class")

        if (rubricClass == "leaf") {
            // Нажатие на рубрику (независимо от наличия вложенных рубрик)
            wait.until(ExpectedConditions.elementToBeClickable(expandRubricChildRubricsTag)).click()
            println("Клик выполнен по SelByCod")

            println("Рубрика является листом, вложенных рубрик нет.")
        } else if (rubricClass == "closed") {
            println("Рубрика закрыта, раскрываем вложенные рубрики...")

            // 1️⃣ Кликаем по кнопке раскрытия вложенных рубрик
            wait.until(ExpectedConditions.elementToBeClickable(expandRubricChildRubricsTag)).click()

            // 2️⃣ Ожидаем, пока рубрика сменит `class` на `"open"`
            val fluentWait = FluentWait(driver)
                .withTimeout(Duration.ofSeconds(10))  // Максимальное ожидание
                .pollingEvery(Duration.ofMillis(500)) // Проверка каждые 500 мс
                .ignoring(StaleElementReferenceException::class.java, NoSuchElementException::class.java)

            // 3️⃣ Ожидаем исчезновения индикатора загрузки
            try {
                fluentWait.until {
                    val loadingIndicator = rubricTag.findElements(By.xpath(".//ul[@class='clsShown']//div[@class='clsLoadMsg']"))
                    loadingIndicator.isEmpty() // Ждём, пока индикатор исчезнет
                }
                println("Индикатор загрузки исчез, вложенные рубрики загружены!")

                // 4️⃣ Ожидаем появления хотя бы одного элемента <li> внутри <ul class="clsShown">
                fluentWait.until {
                    val childRubrics = rubricTag.findElements(By.xpath(".//ul[@class='clsShown']/li"))
                    childRubrics.isNotEmpty()
                }
                println("Вложенные рубрики появились!")
            } catch (e: TimeoutException) {
                println("Не удалось дождаться загрузки вложенных рубрик!")
            }
        } else {
            println("Рубрика уже открыта.")
        }

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

        // Чтение содержимого рубрики
        val vinitiRubricatorNode = parseRubricDescription(nodeViewFormTag)

        // Вернуться на фрейм списка
        driver.switchTo().defaultContent()
        println("Переключено на главный фрейм")
        wait.until(ExpectedConditions.frameToBeAvailableAndSwitchToIt(By.id("fraTree")))
        println("Переключено на fraTree")

        if (vinitiRubricatorNode == null) {
            return null
        }

        if (rubricClass != "leaf") {
            // Проверка наличия вложенных рубрик
            try {

                // Тег, содержащий список дочерних рубрик
                wait.until { rubricTag.findElements(By.className("clsShown")).isNotEmpty() }

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
                catch (_: Exception) { }

                for (childRubricTag in childRubricTagList) {
                    var childNode: AbstractRubricatorNode?
                    try {
                        childNode = parseRubricRecursively(childRubricTag)
                    }
                    catch (ex: Exception) {
                        println("УПАЛО НА ЧТЕНИИ ДОЧЕРНИХ")
                        childNode = null
                    }
                    if (childNode != null) {
                        vinitiRubricatorNode.addChildNode(childNode)
                    }
                }
            } catch (_: NoSuchElementException) { println("Упало на поиске вложенных рубрик") }
            catch (_: TimeoutException) { println("Упало на поиске вложенных рубрик") }
        }

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
        val wait = FluentWait(driver)
            .withTimeout(Duration.ofSeconds(40))  // Общее ожидание
            .pollingEvery(Duration.ofMillis(500)) // Проверка каждые 500 мс
            .ignoring(java.util.NoSuchElementException::class.java)


        var tableRowTagList: List<WebElement>
        try {
             tableRowTagList = try {
                wait.until {
                    val elements = driver.findElements(By.xpath("//tr[.//td[contains(@class, 'NodeFieldName')]]"))
                    if (elements.isNotEmpty()) elements else null
                }
            } catch (e: Exception) {
                println("Ошибка при поиске таблицы: ${e.message}")
                emptyList() // Возвращаем пустой список, чтобы избежать null
            }
        }
        catch (_: Exception) {
            tableRowTagList = try {
                wait.until {
                    val elements = driver.findElements(By.xpath("//tr[.//td[contains(@class, 'NodeFieldName')]]"))
                    if (elements.isNotEmpty()) elements else null
                }
            } catch (e: Exception) {
                println("Ошибка при поиске таблицы: ${e.message}")
                emptyList() // Возвращаем пустой список, чтобы избежать null
            }
        }
        println("Пережил поиск NodeFieldName")

        // Строка с шифром
        var originalCipher: String
        try {
            val cipherRowTag = tableRowTagList.first { tableRowTag -> tableRowTag.findElement(By.className("NodeFieldName")).getAttribute("innerHTML") == "Ориг. шифр" }
            originalCipher = cipherRowTag.findElement(By.className("NodeFieldValue")).getAttribute("innerHTML")!!
        }
        catch (e: Exception) {
            originalCipher = "УПАЛО_УПАЛО"
        }

        // Оригинальный шифр
        println("Шифр: $originalCipher")

        // Аспекты не рассматриваются в реализации
        if (originalCipher.lowercase().contains("аспект")) {
            return null
        }

        if (originalCipher == "") {
            read = true
        }

        // Ссылка на окно с ключевыми словами
        val openKeywordsPopupLinkTag = WebDriverWait(driver, Duration.ofSeconds(30))
            .until(ExpectedConditions.elementToBeClickable(By.xpath(".//a[contains(@onclick, 'OpenKeywords')]")))
        println("Окно ключевые")

        // Извлечение ключевых слов из открываемого окна

        var keywordList: List<String>?

        // Текущее окно
        val mainWindow = driver.windowHandle
        try {
            keywordList = processKeywordWindow(openKeywordsPopupLinkTag)
        }
        catch (e: Exception) {
            println("Закрытие лишних окон...")
            closeAllWindowsExceptMain(mainWindow)
            keywordList = listOf("УПАЛО_УПАЛО_УПАЛО")
        }

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

            val rubricNode = VinitiRubricatorNode(
                originalCipher,
                rubricName,
                keywordList,
                vinitiParentNodeCipher=cscstiRubricCipher
            )

            writeToFileSafe(objectMapper.writeValueAsString(rubricNode))

            return rubricNode
        }
        else {
            throw Exception("Рубрикатор не поддерживается")
        }
    }

    // Функция для безопасной записи строк в файл
    fun writeToFileSafe(text: String) {
        try {
            printWriter.println(text) // Записываем строку
            printWriter.flush() // Сбрасываем буфер, чтобы данные точно записались
        } catch (e: Exception) {
            println("Ошибка при записи в файл: ${e.message}")
        }
    }

    private fun processKeywordChapter(): List<String> {

        try {
            WebDriverWait(driver, Duration.ofSeconds(10))
                .until(ExpectedConditions.presenceOfElementLocated(By.id("kwData")))
        }
        catch (_: Exception) {
            return listOf()
        }

        // Ожидание загрузки ключевых слов
        val keywordSpanTag = waitForKwDataUpdate() ?: return listOf()

        // Ожидание загрузки всех td элементов
         WebDriverWait(driver, Duration.ofSeconds(40))
                        .until(ExpectedConditions.presenceOfAllElementsLocatedBy(By.xpath(".//td")))

        // Тело таблицы
        val keywordTable: WebElement
        try {
            keywordTable = keywordSpanTag.findElement(By.tagName("tbody"))
        }
        catch (_: Exception) {
            return listOf()
        }

        // tr элементы
        val tdTagList = keywordTable.findElements(By.tagName("tr"))

        if (tdTagList.size == 0) {
            return listOf()
        }

        val keywordList = tdTagList.map { it.findElement(By.tagName("td")).getAttribute("innerHTML")!! }
        println(keywordList)

        return keywordList
    }

    fun waitForKwDataUpdate(): WebElement? {
        val wait = FluentWait(driver)
            .withTimeout(Duration.ofSeconds(15))  // Максимальное ожидание
            .pollingEvery(Duration.ofMillis(500)) // Проверка каждые 500 мс
            .ignoring(StaleElementReferenceException::class.java, NoSuchElementException::class.java) // Игнорируем ошибки

        return wait.until {
            val element = driver.findElement(By.id("kwData")) // Переопределяем элемент
            val content = element.getAttribute("innerHTML")?.trim() ?: ""
            if (!content.contains("Получение данных...")) element else null
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
                .ignoring(Exception::class.java) // Игнорируем ошибку отсутствия элемента

            val elements = customWait.until {
                driver.findElements(By.xpath(".//a[contains(@onclick, 'SetWinLocation')]"))
            }.filterNotNull()

            if (elements.isNotEmpty()) {
                println("Элементы найдены")
            } else {
                println("Элементы не найдены")
                closeAllWindowsExceptMain(mainWindow)
                return ""
            }
        }
        catch (e: Exception) {

            // Отображения нет, закрытие текущего окна, переключение на основное (fraNode)
            closeAllWindowsExceptMain(mainWindow)
            return ""
        }

        // Получение тега-ссылки
        val rubricLinkTag = driver.findElement(By.xpath(".//a[contains(@onclick, 'SetWinLocation')]"))

        // Извлечение содержимого из тега
        val linkInner = rubricLinkTag.getAttribute("innerHTML")

        // Извлечение шифра тега
        val cscstiRubricCipher = linkInner!!.split(" ")[0]

        // Закрытие текущего окна, переключение на основное (fraNode)
        closeAllWindowsExceptMain(mainWindow)

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
            closeAllWindowsExceptMain(mainWindow)

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

            val fluentWait = FluentWait(tdTag)
                .withTimeout(Duration.ofSeconds(5))  // Максимальное ожидание
                .pollingEvery(Duration.ofMillis(500)) // Частота проверок
                .ignoring(Exception::class.java) // Игнорируем ошибки

            try {
                fluentWait.until { tdTag.findElements(By.tagName("a")).isNotEmpty() }
            } catch (e: Exception) {
                println("Ссылки не найдены (1 страница)")
                break
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
        closeAllWindowsExceptMain(mainWindow)

        return keywordList
    }

    fun updateKeywordPageState(linkTagList: List<WebElement>, currentPageNumber: String): Boolean {

        var linkTextList: List<String>? = mutableListOf()
        val fluentWait = FluentWait(driver)
            .withTimeout(Duration.ofSeconds(7))  // Общее время ожидания
            .pollingEvery(Duration.ofMillis(500)) // Частота проверок
            .ignoring(Exception::class.java) // Игнорируем ошибки

        linkTextList = fluentWait.until {
            val texts = linkTagList.mapNotNull { it.getAttribute("innerHTML") }
            if (texts.isNotEmpty() && texts.all { it.isNotBlank() }) texts else null
        }

        // Больше 20 страниц считывать нет ресурса
        if (currentPageNumber == "20") {
            return true
        }

        if (linkTextList != null) {
            for ((i, linkText) in linkTextList.withIndex()) {

                // ... в начале списка не интересует
                if (i == 0 && linkText == "...") {
                    continue
                }
                // Если это число
                else if (linkText != "...") {

                    // Числовое представление
                    val currentPageInt = currentPageNumber.toInt()
                    val iteratePageInt = linkText.toInt()

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
            .ignoring(Exception::class.java) // Если элемент долго загружается

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

    fun closeAllWindowsExceptMain(mainWindow: String) {
        val allWindows = driver.windowHandles

        for (window in allWindows) {
            if (window != mainWindow) {
                driver.switchTo().window(window)
                driver.close()
                println("Закрыто окно: $window")
            }
        }

        // Переключаемся обратно на главное окно
        driver.switchTo().window(mainWindow)
        println("Переключено на главное окно")

        // Переключаемся на главный фрейм
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