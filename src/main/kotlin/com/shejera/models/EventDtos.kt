package com.shejera.models

import kotlinx.serialization.Serializable

@Serializable
data class CreateIndividualEventRequest(
    val tag: String,
    val eventType: String? = null,
    val dateText: String? = null,
    val dateSort: String? = null,
    val placeName: String? = null,
    val description: String? = null,
)

@Serializable
data class IndividualEventResponse(
    val id: String,
    val tag: String,
    val eventType: String? = null,
    val dateText: String? = null,
    val dateSort: String? = null,
    val placeName: String? = null,
    val description: String? = null,
)

@Serializable
data class CreateFamilyEventRequest(
    val tag: String,
    val eventType: String? = null,
    val dateText: String? = null,
    val dateSort: String? = null,
    val placeName: String? = null,
    val description: String? = null,
)
