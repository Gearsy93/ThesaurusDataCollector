package com.gearsy.thesaurusdatacollector.deprecated.controller

import com.gearsy.thesaurusdatacollector.model.VinitiRubricatorNode
import com.gearsy.thesaurusdatacollector.service.VinitiWebScraperService
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/web-scrapper/viniti")
class VinitiWebScraperController(private val vinitiWebScraperService: VinitiWebScraperService) {

    @RequestMapping(
        path = ["/tree"],
        method = [RequestMethod.GET],
        consumes = [MediaType.ALL_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    fun scrapeWholeRubricTree() {
        return vinitiWebScraperService.scrapeWholeRubricTree()
    }
}