package com.gearsy.thesaurusdatacollector.service

import com.gearsy.thesaurusdatacollector.model.InionRanDescriptor
import org.springframework.stereotype.Service
import java.nio.file.Paths
import kotlin.io.path.pathString

@Service
class PdfThesaurusParserService {

    val thesaurusPdfFolder = Paths.get("src", "resources", "input", "thesaurus", "pdf").pathString

    fun parsePdfFile(fileName: String): List<InionRanDescriptor> {

        val filePath = Paths.get(thesaurusPdfFolder, fileName)

        val fileDescriptorList: MutableList<InionRanDescriptor> = mutableListOf()

        return fileDescriptorList
    }
}