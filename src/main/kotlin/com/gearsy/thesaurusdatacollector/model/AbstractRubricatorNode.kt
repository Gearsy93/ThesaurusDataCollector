package com.gearsy.thesaurusdatacollector.model

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonManagedReference

@JsonInclude(JsonInclude.Include.NON_NULL)
abstract class AbstractRubricatorNode(
    open val cipher: String,
    open val title: String,
    open var termList: List<String>? = null,
    open var parentCipher: String? = null,
    open var linkCipherList: List<String>? = null,

    @JsonManagedReference
    open var children: MutableList<AbstractRubricatorNode> = mutableListOf()
) {
    fun addChildNode(child: AbstractRubricatorNode) {
        child.parentCipher = this.cipher
        children.add(child)
    }
}