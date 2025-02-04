package com.gearsy.thesaurusdatacollector.model

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonManagedReference

@JsonInclude(JsonInclude.Include.NON_NULL)
data class VinitiRubricatorNode(
    val code: Int,
    val rubricName: String,
    val termList: List<String>,
    var parentId: Int,

    @JsonManagedReference
    val children: MutableList<VinitiRubricatorNode> = mutableListOf()

) {
    fun addChildNode(child: VinitiRubricatorNode) {
        child.parentId = this.code
        children.add(child)
    }
}