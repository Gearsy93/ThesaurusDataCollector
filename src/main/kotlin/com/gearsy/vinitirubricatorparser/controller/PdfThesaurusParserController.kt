package com.gearsy.thesaurusdatacollector.controller

import com.gearsy.thesaurusdatacollector.model.InionRanDescriptor
import com.gearsy.thesaurusdatacollector.service.PdfThesaurusParserService
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/pdf-parser/inion-ran")
class PdfThesaurusParserController(private val pdfThesaurusParserService: PdfThesaurusParserService) {

    @RequestMapping(
        path = ["/parse"],
        method = [RequestMethod.GET],
        consumes = [MediaType.ALL_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    fun parsePdfFile(fileName: String): List<InionRanDescriptor> {
        return pdfThesaurusParserService.parsePdfFile(fileName)
    }
}