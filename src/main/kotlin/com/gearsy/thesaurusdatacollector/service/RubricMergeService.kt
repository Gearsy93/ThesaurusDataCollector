package com.gearsy.thesaurusdatacollector.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.stereotype.Service
import java.io.File

@Service
class RubricMergeService {

    // Маппер объектов для дереализации
    val objectMapper = jacksonObjectMapper()

    fun enrichCSCSTIRootRubricWithVinitiKeyword(cscstiRubricCipher: String, vinitiRubricCiphers: String) {
        val objectMapper = ObjectMapper()

        // Загрузка содержимого рубрики ГРНТИ
        val cscstiFilePath = "src/main/resources/output/rubricator/cscsti/${cscstiRubricCipher}.json"
        val cscstiFileContent = File(cscstiFilePath).readText(Charsets.UTF_8)
        val cscstiJsonNode = objectMapper.readTree(cscstiFileContent)

        // Разбираем переданные шифры ВИНИТИ (например, "691,692")
        val vinitiCiphers = vinitiRubricCiphers.split(",").map { it.trim() }

        // Загружаем все файлы ВИНИТИ и собираем ключевые слова
        val vinitiKeywordsMap = mutableMapOf<String, MutableSet<String>>()

        for (vinitiCipher in vinitiCiphers) {
            val vinitiFilePath = "src/main/resources/output/rubricator/viniti/${vinitiCipher}.json"
            val vinitiFile = File(vinitiFilePath)

            if (!vinitiFile.exists()) {
                println("Файл $vinitiFilePath не найден, пропускаем.")
                continue
            }

            val vinitiJsonNode = objectMapper.readTree(vinitiFile)
            extractVinitiKeywords(vinitiJsonNode, vinitiKeywordsMap)
        }

        // Обогащаем рубрики ГРНТИ ключевыми словами из ВИНИТИ
        val cscstiJsonNodeEnriched = updateCSCSTI(cscstiJsonNode, vinitiKeywordsMap, objectMapper)

        // Сериализация объекта
        val cscstiJsonNodeEnrichedText = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(cscstiJsonNodeEnriched)

        // Сохранение итоговой структуры в JSON-файл
        val cscstiEnrichFilePath = "src/main/resources/output/rubricator/cscstiEnrichedWithViniti/${cscstiRubricCipher}.json"
        File(cscstiEnrichFilePath).writeText(cscstiJsonNodeEnrichedText)

        println("Файл сохранен: $cscstiEnrichFilePath")
    }

    // Извлекаем ключевые слова из ВИНИТИ
    private fun extractVinitiKeywords(node: JsonNode, vinitiKeywordsMap: MutableMap<String, MutableSet<String>>) {
        val vinitiParentCipher = node.get("vinitiParentNodeCipher")?.asText() ?: ""
        if (vinitiParentCipher.isNotBlank()) {
            val keywords = node.get("termList")?.map { it.asText() }?.toMutableSet() ?: mutableSetOf()
            vinitiKeywordsMap.merge(vinitiParentCipher, keywords) { old, new -> old.union(new).toMutableSet() }
        }
        node.get("children")?.forEach { extractVinitiKeywords(it, vinitiKeywordsMap) }
    }

    // Обновляем структуру ГРНТИ
    private fun updateCSCSTI(node: JsonNode, vinitiKeywordsMap: MutableMap<String, MutableSet<String>>, objectMapper: ObjectMapper): JsonNode {
        val cipher = node.get("cipher")?.asText() ?: return node

        // Собираем ключевые слова (изначальные + из ВИНИТИ)
        val existingKeywords = node.get("termList")?.map { it.asText() }?.toMutableSet() ?: mutableSetOf()
        val vinitiKeywords = vinitiKeywordsMap[cipher] ?: emptySet()

        val updatedKeywords = (existingKeywords + vinitiKeywords).toMutableSet() // Убираем дубликаты

        // Создаём объект без дублирующего `content`
        val enrichedNode = node.deepCopy<ObjectNode>()

        // Обновляем `termList`, создавая список объектов с `rubricatorId`
        val newTermList = objectMapper.createArrayNode()
        updatedKeywords.forEach { keyword ->
            val termObject = objectMapper.createObjectNode()
            termObject.put("content", keyword)
            termObject.put("rubricatorId", if (existingKeywords.contains(keyword)) 1 else 2) // 1 - ГРНТИ, 2 - ВИНИТИ
            newTermList.add(termObject)
        }
        enrichedNode.set<ArrayNode>("termList", newTermList)

        // Рекурсивно обновляем дочерние узлы
        val childrenArray = objectMapper.createArrayNode()
        node.get("children")?.forEach { childrenArray.add(updateCSCSTI(it, vinitiKeywordsMap, objectMapper)) }
        enrichedNode.set<ArrayNode>("children", childrenArray)

        return enrichedNode
    }

    fun linkRubricParse(cscstiRubricCipher: String) {
        val objectMapper = ObjectMapper()

        // Загрузка содержимого JSON-файла
        val cscstiFilePath = "src/main/resources/output/rubricator/cscsti/${cscstiRubricCipher}.json"
        val cscstiFileContent = File(cscstiFilePath).readText(Charsets.UTF_8)
        val cscstiJsonNode = objectMapper.readTree(cscstiFileContent)

        // Сет для хранения уникальных рубрик-ссылок
        val uniqueLinkRubrics = mutableSetOf<String>()

        // Рекурсивный обход структуры, сбор всех `linkCipherList`
        fun extractLinks(node: JsonNode) {
            node.get("linkCipherList")?.forEach { link ->
                uniqueLinkRubrics.add(link.asText())
            }
            node.get("children")?.forEach { extractLinks(it) }
        }
        extractLinks(cscstiJsonNode)

        val uniqueRootRubrics = uniqueLinkRubrics.map { it.split(".").first() }.toSet()

        println("Уникальные рубрики-ссылки: ${uniqueLinkRubrics.joinToString(", ")}")
        println("Корневые рубрики: ${uniqueRootRubrics.joinToString(", ")}")
    }

    fun fillWithLinkRubricKeywords(cscstiRubricCipher: String) {
        val objectMapper = ObjectMapper()

        // Загрузка содержимого JSON-файла ГРНТИ
        val cscstiFilePath = "src/main/resources/output/rubricator/cscstiEnrichedWithViniti/${cscstiRubricCipher}.json"
        val cscstiFile = File(cscstiFilePath)
        if (!cscstiFile.exists()) {
            println("Файл $cscstiFilePath не найден!")
            return
        }
        val cscstiJsonNode = objectMapper.readTree(cscstiFile)

        // Обогащаем ключевые слова
        enrichRubricKeywords(cscstiJsonNode, objectMapper)

        // Сохраняем результат
        val outputFilePath = "src/main/resources/output/rubricator/cscstiEnrichedWithLinkRubric/${cscstiRubricCipher}.json"
        File(outputFilePath).writeText(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(cscstiJsonNode))

        println("Обогащение завершено. Файл сохранен в $outputFilePath")
    }

    private fun enrichRubricKeywords(node: JsonNode, objectMapper: ObjectMapper) {
        if (node.has("linkCipherList") && node["linkCipherList"].isArray) {
            val linkCipherList = node["linkCipherList"].map { it.asText() }

            val additionalKeywords = mutableListOf<JsonNode>()

            for (linkedCipher in linkCipherList) {
                val linkedFilePath = "src/main/resources/output/rubricator/cscstiEnrichedWithViniti/${linkedCipher.substringBefore('.')}.json"
                val linkedFile = File(linkedFilePath)
                if (!linkedFile.exists()) {
                    println("Файл с рубрикой $linkedCipher не найден ($linkedFilePath), пропускаем.")
                    continue
                }

                val linkedJsonNode = objectMapper.readTree(linkedFile)
                val linkedRubricNode = findRubricByCipher(linkedJsonNode, linkedCipher)

                if (linkedRubricNode != null && linkedRubricNode.has("termList")) {
                    val linkedKeywords = linkedRubricNode["termList"].map { it }
                    additionalKeywords.addAll(linkedKeywords)
                }
            }

            // Добавляем дополнительные ключевые слова
            if (additionalKeywords.isNotEmpty() && node is ObjectNode) {
                val termListNode = node.withArray<JsonNode>("termList") as ArrayNode
                additionalKeywords.forEach { termListNode.add(it) }
                (node as ObjectNode).remove("linkCipherList") // Удаляем linkCipherList, т.к. он уже не нужен
            }
        }

        // Рекурсивно обрабатываем дочерние элементы
        if (node.has("children") && node["children"].isArray) {
            node["children"].forEach { enrichRubricKeywords(it, objectMapper) }
        }
    }

    private fun findRubricByCipher(rootNode: JsonNode, cipher: String): JsonNode? {
        if (rootNode["cipher"]?.asText() == cipher) {
            return rootNode
        }

        if (rootNode.has("children") && rootNode["children"].isArray) {
            for (child in rootNode["children"]) {
                val foundNode = findRubricByCipher(child, cipher)
                if (foundNode != null) return foundNode
            }
        }
        return null
    }

}