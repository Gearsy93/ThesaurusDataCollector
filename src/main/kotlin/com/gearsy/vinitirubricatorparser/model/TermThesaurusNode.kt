package com.gearsy.vinitirubricatorparser.model

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonManagedReference
import java.util.*

@JsonInclude(JsonInclude.Include.NON_NULL)
data class VinitiRubricatorNode(
    val code: Int,
    val rubricName: String,
    val termList: List<String>,
    @JsonManagedReference
    val children: MutableList<VinitiRubricatorNode> = mutableListOf(),
    var parentId: Int
) {
    fun addChildNode(child: VinitiRubricatorNode) {
        child.parentId = this.code
        children.add(child)
    }
}