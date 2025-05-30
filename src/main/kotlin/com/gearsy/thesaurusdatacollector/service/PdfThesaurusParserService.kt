package com.gearsy.thesaurusdatacollector.service

import org.springframework.stereotype.Service
import java.nio.file.Paths
import kotlin.io.path.pathString

// TODO Заготовка для парсинга pdf-тезаурусов

@Service
class PdfThesaurusParserService {
//
//    val thesaurusPdfFolder = Paths.get("src", "main", "resources", "input", "thesaurus", "pdf").pathString
//
//    fun parsePdfFile(fileName: String): List<InionRanDescriptor> {
//
//
//        // Путь до .pdf файла
//        val pdfFilePath = Paths.get(thesaurusPdfFolder, fileName).toAbsolutePath().toString()
//
//        // Объект .pdf файла
//        val pdfFile = File(pdfFilePath)
//
//        // Извлечение дескрипторов из pdf-файла
//        val fileDescriptorList = extractPdfDescriptors(pdfFile)
//
//        return fileDescriptorList
//    }
//
//    fun extractPdfDescriptors(pdfFile: File): List<InionRanDescriptor> {
//
//        // Список извлеченных дескрипторов
//        val fileDescriptorList: MutableList<InionRanDescriptor> = mutableListOf()
//
//        val pagesText = mutableListOf<String>()
//
//        Loader.loadPDF(pdfFile).use { document ->
//            val pdfStripper = PDFTextStripper()
//
//            var isFoundALXIndexPage = false
//
//            // Итерация по страницам файла
//            for (i in 1..document.numberOfPages) {
//                pdfStripper.startPage = i
//                pdfStripper.endPage = i
//                val pageText = pdfStripper.getText(document).trim()
//
//                if (isFoundALXIndexPage) {
//                    pagesText.add(pageText)
//                }
//                else if (!pageText.contains("СОДЕРЖАНИЕ", ignoreCase = true)
//                    && !pageText.contains("УКАЗАТЕЛЬ.", ignoreCase = true)
//                    && !pageText.contains("указатель используется", ignoreCase = true)
//                    && pageText.contains("АЛФАВИТНЫЙ ЛЕКСИКО-СЕМАНТИЧЕСКИЙ УКАЗАТЕЛЬ", ignoreCase = true)) {
//                    isFoundALXIndexPage = true
//                    pagesText.add(pageText)
//                }
//            }
//        }
//
//        return fileDescriptorList
//    }
}