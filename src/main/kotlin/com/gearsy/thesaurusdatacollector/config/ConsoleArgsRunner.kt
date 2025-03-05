package com.gearsy.thesaurusdatacollector.config

import com.gearsy.thesaurusdatacollector.service.RubricMergeService
import com.gearsy.thesaurusdatacollector.service.VinitiWebScraperService
import org.springframework.boot.CommandLineRunner
import org.springframework.stereotype.Component

@Component
class ConsoleArgsRunner(
    private val vinitiWebScraperService: VinitiWebScraperService,
    private val rubricMergeService: RubricMergeService
) : CommandLineRunner {

    override fun run(vararg args: String?) {
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
            arguments.contains("-enrich_cscsti") -> {
                val index = arguments.indexOf("-enrich_cscsti")
                if (arguments.size > index + 2) {
                    val cscstiCipher = arguments[index + 1]
                    val vinitiCipher = arguments[index + 2]
                    println("Обогащение рубрики CSCSTI ($cscstiCipher) ключевыми словами VINITI ($vinitiCipher)")
                    rubricMergeService.enrichCSCSTIRootRubricWithVinitiKeyword(cscstiCipher, vinitiCipher)
                } else {
                    println("Ошибка: флаг -enrich_cscsti требует два аргумента: <cscstiCipher> <vinitiCipher>")
                    printUsage()
                }
            }
            arguments.contains("-list_link_rubric") -> {
                val index = arguments.indexOf("-list_link_rubric")
                if (arguments.size > index + 1) {
                    val rubricCipher = arguments[index + 1]
                    println("Список ссылочных рубрик для рубрики ГРНТИ $rubricCipher")
                    rubricMergeService.linkRubricParse(rubricCipher)
                } else {
                    println("Ошибка: флаг -list_link_rubric требует указания шифра рубрики.")
                    printUsage()
                }
            }
            arguments.contains("-fill_link_keyword") -> {
                val index = arguments.indexOf("-fill_link_keyword")
                if (arguments.size > index + 1) {
                    val rubricCipher = arguments[index + 1]
                    println("Заполнение ключевых слов по ссылочным рубрикам ГРНТИ $rubricCipher")
                    rubricMergeService.fillWithLinkRubricKeywords(rubricCipher)
                } else {
                    println("Ошибка: флаг -fill_link_keyword требует указания шифра рубрики.")
                    printUsage()
                }
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
              -parse_cscsti <cipher>         Извлечь структуру ГРНТИ с сайта ВИНИТИ для указанного шифра рубрики
              -fillNeo4j <filename>          Заполнить схему Neo4j с указанным именем файла (без расширения)
              -clearNeo4j                    Очистить базу данных Neo4j
              -enrich_cscsti <cscsti> <viniti> Обогатить рубрику CSCSTI ключевыми словами из рубрики VINITI
              -list_link_rubric <cipher>     Список ссылочных рубрик для рубрики ГРНТИ
            """.trimIndent()
        )
    }
}