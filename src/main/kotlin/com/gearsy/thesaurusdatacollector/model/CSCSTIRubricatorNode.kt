package com.gearsy.thesaurusdatacollector.model

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonManagedReference

@JsonInclude(JsonInclude.Include.NON_NULL)
data class CSCSTIRubricatorNode(
    override val cipher: String,
    override val title: String,
    override var termList: List<String>? = null,
    override var parentCipher: String? = null,
    override var linkCipherList: List<String>? = null,

    @JsonManagedReference
    override var children: MutableList<AbstractRubricatorNode> = mutableListOf()
) : AbstractRubricatorNode(cipher, title, termList, parentCipher, linkCipherList, children)