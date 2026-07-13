package com.shejera.services

import com.shejera.api.BadRequestException
import com.shejera.api.NotFoundException
import com.shejera.db.generated.tables.records.FamilyRecord
import com.shejera.models.AddChildRequest
import com.shejera.models.ChildResponse
import com.shejera.models.CreateFamilyEventRequest
import com.shejera.models.CreateFamilyRequest
import com.shejera.models.FamilyEventResponse
import com.shejera.models.FamilyResponse
import com.shejera.models.SpouseResponse
import com.shejera.repositories.FamilyRepository
import com.shejera.repositories.IndividualRepository
import com.shejera.repositories.PlaceRepository
import com.shejera.repositories.TreeRepository
import org.jooq.DSLContext
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.UUID

class FamilyService(
    private val dsl: DSLContext,
    private val treeRepository: TreeRepository,
    private val individualRepository: IndividualRepository,
    private val familyRepository: FamilyRepository,
    private val placeRepository: PlaceRepository,
) {
    fun list(): List<FamilyResponse> {
        val treeId = treeRepository.getDefaultTreeId()
        return familyRepository.listByTree(treeId).map { toResponse(it) }
    }

    fun get(id: UUID): FamilyResponse {
        val treeId = treeRepository.getDefaultTreeId()
        val family =
            familyRepository.findByIdAndTree(id, treeId)
                ?: throw NotFoundException("Family not found: $id")
        return toResponse(family)
    }

    fun create(request: CreateFamilyRequest): FamilyResponse {
        if (request.spouses.isEmpty()) {
            throw BadRequestException("At least one spouse is required")
        }
        if (request.spouses.size > 2) {
            throw BadRequestException("At most two spouses are supported in v1")
        }

        request.spouses.forEach { spouse ->
            validateRole(spouse.role)
        }

        val treeId = treeRepository.getDefaultTreeId()

        val family =
            dsl.transactionResult { ctx ->
                val txDsl = ctx.dsl()
                val txFamilyRepo = FamilyRepository(txDsl)
                val txIndividualRepo = IndividualRepository(txDsl)
                val txPlaceRepo = PlaceRepository(txDsl)

                request.spouses.forEach { spouse ->
                    val individualId = parseUuid(spouse.individualId, "individualId")
                    txIndividualRepo.findByIdAndTree(individualId, treeId)
                        ?: throw BadRequestException("Individual not found: ${spouse.individualId}")
                }

                val xref = txFamilyRepo.nextXref(treeId)
                val created = txFamilyRepo.insert(treeId, xref)

                request.spouses.forEachIndexed { index, spouse ->
                    txFamilyRepo.insertSpouse(
                        familyId = created.id!!,
                        individualId = parseUuid(spouse.individualId, "individualId"),
                        role = spouse.role,
                        sortOrder = index,
                    )
                }

                request.marriage?.let { marriage ->
                    val placeId = marriage.placeName?.let { txPlaceRepo.findOrCreate(it).id }
                    txFamilyRepo.insertEvent(
                        familyId = created.id!!,
                        tag = "MARR",
                        eventType = null,
                        dateText = marriage.dateText,
                        dateSort = parseDate(marriage.dateSort),
                        placeId = placeId,
                        description = marriage.description,
                    )
                }

                created
            }

        return toResponse(family)
    }

    fun delete(id: UUID) {
        val treeId = treeRepository.getDefaultTreeId()
        familyRepository.findByIdAndTree(id, treeId)
            ?: throw NotFoundException("Family not found: $id")

        if (!familyRepository.delete(id)) {
            throw NotFoundException("Family not found: $id")
        }
    }

    fun addChild(
        familyId: UUID,
        request: AddChildRequest,
    ): ChildResponse {
        val treeId = treeRepository.getDefaultTreeId()
        familyRepository.findByIdAndTree(familyId, treeId)
            ?: throw NotFoundException("Family not found: $familyId")

        val individualId = parseUuid(request.individualId, "individualId")
        val individual =
            individualRepository.findByIdAndTree(individualId, treeId)
                ?: throw BadRequestException("Individual not found: ${request.individualId}")

        validatePedigree(request.pedigree)

        val child =
            familyRepository.insertChild(
                familyId = familyId,
                individualId = individualId,
                pedigree = request.pedigree,
                sortOrder = request.sortOrder,
            )

        val name = individualRepository.findPreferredName(individualId)

        return ChildResponse(
            individualId = individualId.toString(),
            xref = individual.xref!!,
            givenName = name?.givenName,
            surname = name?.surname,
            pedigree = child.pedigree,
            sortOrder = child.sortOrder!!,
        )
    }

    fun addEvent(
        familyId: UUID,
        request: CreateFamilyEventRequest,
    ): FamilyEventResponse {
        val treeId = treeRepository.getDefaultTreeId()
        familyRepository.findByIdAndTree(familyId, treeId)
            ?: throw NotFoundException("Family not found: $familyId")

        if (request.tag.isBlank()) {
            throw BadRequestException("tag is required")
        }

        val event =
            dsl.transactionResult { ctx ->
                val txDsl = ctx.dsl()
                val placeId = request.placeName?.let { PlaceRepository(txDsl).findOrCreate(it).id }
                FamilyRepository(txDsl).insertEvent(
                    familyId = familyId,
                    tag = request.tag,
                    eventType = request.eventType,
                    dateText = request.dateText,
                    dateSort = parseDate(request.dateSort),
                    placeId = placeId,
                    description = request.description,
                )
            }

        val placeName = event.placeId?.let { placeRepository.findById(it)?.name }

        return FamilyEventResponse(
            id = event.id.toString(),
            tag = event.tag!!,
            eventType = event.eventType,
            dateText = event.dateText,
            dateSort = event.dateSort?.toString(),
            placeName = placeName,
            description = event.description,
        )
    }

    private fun toResponse(family: FamilyRecord): FamilyResponse {
        val familyId = family.id!!

        return FamilyResponse(
            id = familyId.toString(),
            xref = family.xref!!,
            spouses =
                familyRepository.listSpouses(familyId).map { row ->
                    SpouseResponse(
                        individualId = row.individualId.toString(),
                        xref = row.xref,
                        role = row.role,
                        givenName = row.givenName,
                        surname = row.surname,
                        sortOrder = row.sortOrder,
                    )
                },
            children =
                familyRepository.listChildren(familyId).map { row ->
                    ChildResponse(
                        individualId = row.individualId.toString(),
                        xref = row.xref,
                        givenName = row.givenName,
                        surname = row.surname,
                        pedigree = row.pedigree,
                        sortOrder = row.sortOrder,
                    )
                },
            events =
                familyRepository.listEvents(familyId).map { row ->
                    FamilyEventResponse(
                        id = row.id.toString(),
                        tag = row.tag,
                        eventType = row.eventType,
                        dateText = row.dateText,
                        dateSort = row.dateSort?.toString(),
                        placeName = row.placeName,
                        description = row.description,
                    )
                },
        )
    }

    private fun validateRole(role: String) {
        if (role !in VALID_ROLES) {
            throw BadRequestException("role must be one of: ${VALID_ROLES.joinToString()}")
        }
    }

    private fun validatePedigree(pedigree: String?) {
        if (pedigree == null) return
        if (pedigree !in VALID_PEDIGREE) {
            throw BadRequestException("pedigree must be one of: ${VALID_PEDIGREE.joinToString()}")
        }
    }

    private fun parseUuid(
        value: String,
        field: String,
    ): UUID =
        try {
            UUID.fromString(value)
        } catch (_: IllegalArgumentException) {
            throw BadRequestException("Invalid UUID for $field: $value")
        }

    private fun parseDate(value: String?): LocalDate? {
        if (value.isNullOrBlank()) return null
        return try {
            LocalDate.parse(value)
        } catch (_: DateTimeParseException) {
            throw BadRequestException("dateSort must be ISO format YYYY-MM-DD")
        }
    }

    companion object {
        private val VALID_ROLES = setOf("HUSB", "WIFE")
        private val VALID_PEDIGREE =
            setOf("BIRTH", "ADOPTED", "FOSTER", "SEALING", "STEP", "CHALLENGED", "DISPROVEN")
    }
}
