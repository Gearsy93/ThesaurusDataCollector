package com.gearsy.thesaurusdatacollector.service

import com.gearsy.thesaurusdatacollector.model.InionRanDescriptor
import org.springframework.stereotype.Service

@Service
class PdfThesaurusParserService {

    fun parsePdfFile(fileName: String): List<InionRanDescriptor> {

        val fileDescriptorList: MutableList<InionRanDescriptor> = mutableListOf()

        return fileDescriptorList
    }
}