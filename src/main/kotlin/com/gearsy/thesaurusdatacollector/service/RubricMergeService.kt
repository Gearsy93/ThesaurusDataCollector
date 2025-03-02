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

    fun enrichCSCSTIRootRubricWithVinitiKeyword (cscstiRubricCipher: String, vinitiRubricCipher: String) {

        // Маппер объектов для дереализации
        val objectMapper = jacksonObjectMapper()

        // Загрузка содержимого рубрики ГРНТИ
        val cscstiFilePath = "src/main/resources/output/rubricator/cscsti/${cscstiRubricCipher}.json"
        val cscstiFileContent = File(cscstiFilePath).readText(Charsets.UTF_8)
        val cscstiJsonNode = objectMapper.readTree(cscstiFileContent)


        // Загрузка содержимого рубрики ВИНИТИ
        val vinitiFilePath = "src/main/resources/output/rubricator/viniti/${vinitiRubricCipher}.json"
        val vinitiFileContent = File(vinitiFilePath).readText(Charsets.UTF_8)
        val vinitiJsonNode = objectMapper.readTree(vinitiFileContent)

        // Парсинг структур для обогащения
        val cscstiJsonNodeEnriched = parseJsonToEnrich(cscstiJsonNode, vinitiJsonNode)

        // Сериализация объекта
        val cscstiJsonNodeEnrichedText = objectMapper.writeValueAsString(cscstiJsonNodeEnriched)

        // Сохранение итоговой структуры в JSON-файл
        val cscstiEnrichFilePath = "src/main/resources/output/rubricator/cscstiEnrichedWithViniti/${cscstiRubricCipher}.json"
        File(cscstiEnrichFilePath).writeText(cscstiJsonNodeEnrichedText)

    }

    fun parseJsonToEnrich(cscstiJsonNode: JsonNode, vinitiJsonNode: JsonNode): JsonNode {
        val objectMapper = ObjectMapper()

        // Карта соответствия: ключ – cipher ВИНИТИ, значение – ключевые слова
        val vinitiKeywordsMap = mutableMapOf<String, MutableSet<String>>()

        // 1️⃣ Обходим ВИНИТИ, собирая ключевые слова по vinitiParentNodeCipher
        fun extractVinitiKeywords(node: JsonNode) {
            val vinitiParentCipher = node.get("vinitiParentNodeCipher")?.asText() ?: ""
            if (vinitiParentCipher.isNotBlank()) {
                val keywords = node.get("termList")?.map { it.asText() }?.toMutableSet() ?: mutableSetOf()
                vinitiKeywordsMap.merge(vinitiParentCipher, keywords) { old, new -> old.union(new).toMutableSet() }
            }
            node.get("children")?.forEach { extractVinitiKeywords(it) }
        }
        extractVinitiKeywords(vinitiJsonNode)

        // 2️⃣ Обновляем ГРНТИ с новыми полями
        fun updateCSCSTI(node: JsonNode): JsonNode {
            val cipher = node.get("cipher")?.asText() ?: return node
            val existingKeywords = node.get("termList")?.map { it.asText() }?.toMutableSet() ?: mutableSetOf()
            val updatedKeywords = mutableListOf<Pair<String, Int>>() // Ключевые слова + rubricatorId

            // Добавляем ключевые слова из ГРНТИ (rubricatorId = 1)
            existingKeywords.forEach { updatedKeywords.add(it to 1) }

            // Добавляем ключевые слова из ВИНИТИ (rubricatorId = 2)
            vinitiKeywordsMap[cipher]?.forEach { updatedKeywords.add(it to 2) }

            // Создаём новую структуру рубрики
            val enrichedNode = objectMapper.createObjectNode()

            // Добавляем поле `rubricatorId` (информационные поля)
            val rubricatorIdNode = objectMapper.createObjectNode()
            rubricatorIdNode.put("1", "Государственный рубрикатор научно-технической информации")
            rubricatorIdNode.put("2", "Рубрикатор отраслей знаний ВИНИТИ РАН")
            enrichedNode.set<ObjectNode>("rubricatorId", rubricatorIdNode)

            // Переносим текущее содержимое в `content`
            enrichedNode.set<ObjectNode>("content", node)

            // Обновляем `termList`, создавая список объектов с `rubricatorId`
            val newTermList = objectMapper.createArrayNode()
            updatedKeywords.forEach { (keyword, rubricatorId) ->
                val termObject = objectMapper.createObjectNode()
                termObject.put("content", keyword)
                termObject.put("rubricatorId", rubricatorId)
                newTermList.add(termObject)
            }
            (enrichedNode["content"] as ObjectNode).set<ArrayNode>("termList", newTermList)

            // Рекурсивно обновляем дочерние узлы
            val childrenArray = objectMapper.createArrayNode()
            node.get("children")?.forEach { childrenArray.add(updateCSCSTI(it)) }
            (enrichedNode["content"] as ObjectNode).set<ArrayNode>("children", childrenArray)

            return enrichedNode
        }

        return updateCSCSTI(cscstiJsonNode)
    }
}