package com.gearsy.thesaurusdatacollector.model

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
data class VinitiRubricatorNode(
    override val cipher: String,
    override val title: String,
    override var termList: List<String>? = null,
    override var parentCipher: String? = null,
    override var linkCipherList: List<String>? = null,
    override var children: MutableList<AbstractRubricatorNode> = mutableListOf(),

    var vinitiParentNodeCipher: String? = null
) : AbstractRubricatorNode(cipher, title, termList, parentCipher, linkCipherList, children)