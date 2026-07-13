package com.shejera.models

import kotlinx.serialization.Serializable

@Serializable
data class CreateIndividualRequest(
    val givenName: String,
    val surname: String,
    val sex: String? = null,
    val isLiving: Boolean = true,
    val biography: String? = null,
)

@Serializable
data class UpdateIndividualRequest(
    val sex: String? = null,
    val isLiving: Boolean? = null,
    val givenName: String? = null,
    val surname: String? = null,
    val biography: String? = null,
)

@Serializable
data class IndividualResponse(
    val id: String,
    val xref: String,
    val sex: String? = null,
    val isLiving: Boolean,
    val givenName: String? = null,
    val surname: String? = null,
    val biography: String? = null,
)
