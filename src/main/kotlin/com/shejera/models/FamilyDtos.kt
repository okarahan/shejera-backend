package com.shejera.models

import kotlinx.serialization.Serializable

@Serializable
data class SpouseRequest(
    val individualId: String,
    val role: String,
)

@Serializable
data class MarriageEventRequest(
    val dateText: String? = null,
    val dateSort: String? = null,
    val placeName: String? = null,
    val description: String? = null,
)

@Serializable
data class CreateFamilyRequest(
    val spouses: List<SpouseRequest>,
    val marriage: MarriageEventRequest? = null,
)

@Serializable
data class AddChildRequest(
    val individualId: String,
    val pedigree: String? = "BIRTH",
    val sortOrder: Int = 0,
)

@Serializable
data class SpouseResponse(
    val individualId: String,
    val xref: String,
    val role: String,
    val givenName: String? = null,
    val surname: String? = null,
    val sortOrder: Int,
)

@Serializable
data class ChildResponse(
    val individualId: String,
    val xref: String,
    val givenName: String? = null,
    val surname: String? = null,
    val pedigree: String? = null,
    val sortOrder: Int,
)

@Serializable
data class FamilyEventResponse(
    val id: String,
    val tag: String,
    val eventType: String? = null,
    val dateText: String? = null,
    val dateSort: String? = null,
    val placeName: String? = null,
    val description: String? = null,
)

@Serializable
data class FamilyResponse(
    val id: String,
    val xref: String,
    val spouses: List<SpouseResponse> = emptyList(),
    val children: List<ChildResponse> = emptyList(),
    val events: List<FamilyEventResponse> = emptyList(),
)
