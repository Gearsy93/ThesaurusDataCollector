package com.gearsy.thesaurusdatacollector.model

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
class InionRanDescriptor(
    val name: String,
    val scopeNone: String,
    val narrowerTermList: List<String>,
    val broaderTermList: List<String>,
    val relatedTermList: List<String>,
    val usedForTermList: List<String>
)