package com.gearsy.thesaurusdatacollector.model

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonManagedReference

@JsonInclude(JsonInclude.Include.NON_NULL)
data class VinitiRubricatorNode(
    val cipher: String,
    val title: String,
    var termList: List<String>? = null,
    var parentCipher: String? = null,

    @JsonManagedReference
    var children: MutableList<VinitiRubricatorNode> = mutableListOf()

) {
    fun addChildNode(child: VinitiRubricatorNode) {
        child.parentCipher = this.cipher
        children.add(child)
    }
}