package com.gearsy.thesaurusdatacollector.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.gearsy.thesaurusdatacollector.config.Neo4jProperties
import com.gearsy.thesaurusdatacollector.model.VinitiRubricatorNode
import com.fasterxml.jackson.module.kotlin.readValue
import jakarta.annotation.PreDestroy
import org.neo4j.driver.AuthTokens
import org.neo4j.driver.Driver
import org.neo4j.driver.GraphDatabase
import org.neo4j.driver.Session
import org.springframework.stereotype.Service
import java.io.File

@Service
class Neo4jDBFillerService(
    private val neo4jProperties: Neo4jProperties,
) {

    // Создаем драйвер для подключения
    private var driver: Driver = GraphDatabase.driver(neo4jProperties.uri, AuthTokens.basic(neo4jProperties.username, neo4jProperties.password))

    @PreDestroy
    fun cleanup() {
        driver.close()
    }

    fun fillNeo4jSchemaWithRubricJson(filename: String) {

        // Путь к файлу JSON
        val filePath = "src/main/resources/output/viniti/cscsti/${filename}.json"

        val mapper = jacksonObjectMapper()
        val rootRubric: VinitiRubricatorNode = mapper.readValue(File(filePath))

        // Открываем сессию и заполняем базу
        driver.session().use { session ->
            // Рекурсивно добавляем узлы и связи
            insertRubric(session, rootRubric, parentCipher = null)
        }

    }

    fun insertRubric(session: Session, rubricNode: VinitiRubricatorNode, parentCipher: String?) {
        // Запрос для создания или обновления узла с меткой Rubric
        val query = """
        MERGE (r:Rubric {cipher: ${'$'}cipher})
        SET r.title = ${'$'}title, r.termList = ${'$'}termList
        RETURN r
    """.trimIndent()

        session.run(query, mapOf(
            "cipher" to rubricNode.cipher,
            "title" to rubricNode.title,
            "termList" to rubricNode.termList
        ))

        // Если существует родительская рубрика, создаём связь HAS_CHILD
        if (parentCipher != null) {
            val relQuery = """
            MATCH (parent:Rubric {cipher: ${'$'}parentCipher}), (child:Rubric {cipher: ${'$'}childCipher})
            MERGE (parent)-[:HAS_CHILD]->(child)
        """.trimIndent()
            session.run(relQuery, mapOf(
                "parentCipher" to parentCipher,
                "childCipher" to rubricNode.cipher
            ))
        }

        // Рекурсивно обрабатываем дочерние рубрики
        rubricNode.children.forEach { child ->
            insertRubric(session, child, rubricNode.cipher)
        }
    }

    fun clearDatabase() {
        val driver = GraphDatabase.driver(
            neo4jProperties.uri,
            AuthTokens.basic(neo4jProperties.username, neo4jProperties.password)
        )
        driver.session().use { session ->
            session.run("MATCH (n) DETACH DELETE n")
        }
        driver.close()
        println("База данных Neo4j очищена.")
    }
}