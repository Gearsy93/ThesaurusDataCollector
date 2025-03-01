package com.gearsy.thesaurusdatacollector

import com.gearsy.thesaurusdatacollector.service.Neo4jDBFillerService
import com.gearsy.thesaurusdatacollector.service.VinitiWebScraperService
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.stereotype.Component

@SpringBootApplication
class ThesaurusDataCollectorApplication

fun main(args: Array<String>) {
	runApplication<ThesaurusDataCollectorApplication>(*args)
}

@Component
class ConsoleArgsRunner(
	private val vinitiWebScraperService: VinitiWebScraperService,
	private val neo4jDBFillerService: Neo4jDBFillerService
) : CommandLineRunner {

	override fun run(vararg args: String?) {

		// Убираем возможные null значения
		val arguments = args.filterNotNull()

		if (arguments.isEmpty()) {
			printUsage()
			return
		}

		when {
			arguments.contains("-parse_cscsti") -> {
				val index = arguments.indexOf("-parse_cscsti")
				if (arguments.size > index + 1) {
					val rubricCipher = arguments[index + 1]
					println("Выполняется: Спылесосить ГРНТИ с сайта ВИНИТИ для рубрики $rubricCipher")
					vinitiWebScraperService.scrapeRubricFromTree(rubricCipher)
				} else {
					println("Ошибка: флаг -parse_cscsti требует указания шифра рубрики.")
					printUsage()
				}
			}
			arguments.contains("-fillNeo4j") -> {
				val index = arguments.indexOf("-fillNeo4j")
				if (arguments.size > index + 1) {
					val fileName = arguments[index + 1]
					// Проверка: имя файла не должно содержать точку
					if (fileName.contains('.')) {
						println("Ошибка: имя файла не должно содержать расширение.")
						printUsage()
					} else {
						println("Выполняется: Заполнить схему Neo4j с файлом $fileName.json")
						neo4jDBFillerService.fillNeo4jSchemaWithRubricJson(fileName)
					}
				} else {
					println("Ошибка: флаг -fillNeo4j требует указания имени файла.")
					printUsage()
				}
			}
			arguments.contains("-clearNeo4j") -> {
				println("Выполняется: Очистка базы данных Neo4j")
				neo4jDBFillerService.clearDatabase()
			}
			else -> {
				println("Неизвестные аргументы.")
				printUsage()
			}
		}
	}

	private fun printUsage() {
		println(
			"""
            Использование:
              -parse_cscsti <cipher>   Выполнить сбор данных с сайта ВИНИТИ для указанного шифра рубрики
              -fillNeo4j <filename>    Заполнить схему Neo4j с указанным именем файла (без расширения)
              -clearNeo4j             Очистить базу данных Neo4j
            """.trimIndent()
		)
	}
}