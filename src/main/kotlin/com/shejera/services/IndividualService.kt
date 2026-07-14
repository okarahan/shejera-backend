package com.shejera.services

import com.shejera.api.BadRequestException
import com.shejera.api.ConflictException
import com.shejera.api.NotFoundException
import com.shejera.db.generated.tables.records.IndividualRecord
import com.shejera.models.CreateIndividualEventRequest
import com.shejera.models.CreateIndividualRequest
import com.shejera.models.IndividualEventResponse
import com.shejera.models.IndividualRelationshipsResponse
import com.shejera.models.IndividualResponse
import com.shejera.models.RelatedIndividual
import com.shejera.models.UpdateIndividualRequest
import com.shejera.repositories.EventRepository
import com.shejera.repositories.FamilyRepository
import com.shejera.repositories.IndividualRepository
import com.shejera.repositories.PlaceRepository
import com.shejera.repositories.TreeRepository
import org.jooq.DSLContext
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.UUID

class IndividualService(
    private val dsl: DSLContext,
    private val treeRepository: TreeRepository,
    private val individualRepository: IndividualRepository,
    private val familyRepository: FamilyRepository,
    private val eventRepository: EventRepository,
    private val placeRepository: PlaceRepository,
) {
    fun list(): List<IndividualResponse> {
        val treeId = treeRepository.getDefaultTreeId()
        return individualRepository.listByTree(treeId).map { toResponse(it) }
    }

    fun get(id: UUID): IndividualResponse {
        val treeId = treeRepository.getDefaultTreeId()
        val individual =
            individualRepository.findByIdAndTree(id, treeId)
                ?: throw NotFoundException("Individual not found: $id")
        return toResponse(individual)
    }

    fun create(request: CreateIndividualRequest): IndividualResponse {
        validateSex(request.sex)
        if (request.givenName.isBlank() && request.surname.isBlank()) {
            throw BadRequestException("givenName or surname is required")
        }

        val treeId = treeRepository.getDefaultTreeId()

        val individual =
            dsl.transactionResult { ctx ->
                val txDsl = ctx.dsl()
                val txRepo = IndividualRepository(txDsl)

                val xref = txRepo.nextXref(treeId)
                val created =
                    txRepo.insert(
                        treeId = treeId,
                        xref = xref,
                        sex = request.sex,
                        isLiving = request.isLiving,
                    )

                txRepo.insertName(
                    individualId = created.id!!,
                    givenName = request.givenName,
                    surname = request.surname,
                )

                if (!request.biography.isNullOrBlank()) {
                    txRepo.insertBiographyNote(treeId, created.id!!, request.biography)
                }

                created
            }

        return toResponse(individual)
    }

    fun update(
        id: UUID,
        request: UpdateIndividualRequest,
    ): IndividualResponse {
        validateSex(request.sex)

        val treeId = treeRepository.getDefaultTreeId()
        val existing =
            individualRepository.findByIdAndTree(id, treeId)
                ?: throw NotFoundException("Individual not found: $id")

        dsl.transaction { ctx ->
            val txDsl = ctx.dsl()
            val txRepo = IndividualRepository(txDsl)

            if (request.sex != null || request.isLiving != null) {
                txRepo.update(
                    id = id,
                    sex = request.sex ?: existing.sex,
                    isLiving = request.isLiving ?: existing.isLiving!!,
                )
            }

            if (request.givenName != null || request.surname != null) {
                val currentName = individualRepository.findPreferredName(id)
                txRepo.updatePreferredName(
                    individualId = id,
                    givenName = request.givenName ?: currentName?.givenName,
                    surname = request.surname ?: currentName?.surname,
                )
            }

            if (request.biography != null) {
                val link = txRepo.findBiographyLink(id)
                if (link != null) {
                    txRepo.updateBiographyNote(link.noteId!!, request.biography)
                } else if (request.biography.isNotBlank()) {
                    txRepo.insertBiographyNote(treeId, id, request.biography)
                }
            }
        }

        return get(id)
    }

    fun getRelationships(id: UUID): IndividualRelationshipsResponse {
        val treeId = treeRepository.getDefaultTreeId()
        individualRepository.findByIdAndTree(id, treeId)
            ?: throw NotFoundException("Individual not found: $id")

        val spouses = mutableListOf<RelatedIndividual>()
        val children = mutableListOf<RelatedIndividual>()
        val childIds = mutableSetOf<UUID>()

        for (familyId in familyRepository.listFamilyIdsAsSpouse(id, treeId)) {
            for (spouse in familyRepository.listSpouses(familyId)) {
                if (spouse.individualId != id) {
                    spouses.add(
                        RelatedIndividual(
                            individualId = spouse.individualId.toString(),
                            xref = spouse.xref,
                            givenName = spouse.givenName,
                            surname = spouse.surname,
                            familyId = familyId.toString(),
                            role = spouse.role,
                        ),
                    )
                }
            }
            for (child in familyRepository.listChildren(familyId)) {
                if (childIds.add(child.individualId)) {
                    children.add(
                        RelatedIndividual(
                            individualId = child.individualId.toString(),
                            xref = child.xref,
                            givenName = child.givenName,
                            surname = child.surname,
                            familyId = familyId.toString(),
                            role = null,
                        ),
                    )
                }
            }
        }

        val parents = mutableListOf<RelatedIndividual>()
        val parentFamilyId = familyRepository.findFamilyIdAsChild(id, treeId)
        if (parentFamilyId != null) {
            for (spouse in familyRepository.listSpouses(parentFamilyId)) {
                parents.add(
                    RelatedIndividual(
                        individualId = spouse.individualId.toString(),
                        xref = spouse.xref,
                        givenName = spouse.givenName,
                        surname = spouse.surname,
                        familyId = parentFamilyId.toString(),
                        role = spouse.role,
                    ),
                )
            }
        }

        return IndividualRelationshipsResponse(
            spouses = spouses,
            children = children,
            parents = parents,
        )
    }

    fun delete(id: UUID) {
        val treeId = treeRepository.getDefaultTreeId()
        individualRepository.findByIdAndTree(id, treeId)
            ?: throw NotFoundException("Individual not found: $id")

        val relationships = getRelationships(id)
        if (relationships.children.isNotEmpty()) {
            val names =
                relationships.children.joinToString(", ") { related ->
                    formatName(related.givenName, related.surname, related.xref)
                }
            throw ConflictException(
                "Individual has children and cannot be deleted. Delete children first: $names",
            )
        }

        if (!individualRepository.delete(id)) {
            throw NotFoundException("Individual not found: $id")
        }
    }

    fun addEvent(
        id: UUID,
        request: CreateIndividualEventRequest,
    ): IndividualEventResponse {
        val treeId = treeRepository.getDefaultTreeId()
        individualRepository.findByIdAndTree(id, treeId)
            ?: throw NotFoundException("Individual not found: $id")

        if (request.tag.isBlank()) {
            throw BadRequestException("tag is required")
        }

        val event =
            dsl.transactionResult { ctx ->
                val txDsl = ctx.dsl()
                val placeId = request.placeName?.let { PlaceRepository(txDsl).findOrCreate(it).id }
                EventRepository(txDsl).insertIndividualEvent(
                    individualId = id,
                    tag = request.tag,
                    eventType = request.eventType,
                    dateText = request.dateText,
                    dateSort = parseDate(request.dateSort),
                    placeId = placeId,
                    description = request.description,
                )
            }

        val placeName = event.placeId?.let { placeRepository.findById(it)?.name }

        return IndividualEventResponse(
            id = event.id.toString(),
            tag = event.tag!!,
            eventType = event.eventType,
            dateText = event.dateText,
            dateSort = event.dateSort?.toString(),
            placeName = placeName,
            description = event.description,
        )
    }

    fun listEvents(id: UUID): List<IndividualEventResponse> {
        val treeId = treeRepository.getDefaultTreeId()
        individualRepository.findByIdAndTree(id, treeId)
            ?: throw NotFoundException("Individual not found: $id")

        return eventRepository.listIndividualEvents(id).map { row ->
            IndividualEventResponse(
                id = row.id.toString(),
                tag = row.tag,
                eventType = row.eventType,
                dateText = row.dateText,
                dateSort = row.dateSort?.toString(),
                placeName = row.placeName,
                description = row.description,
            )
        }
    }

    private fun toResponse(individual: IndividualRecord): IndividualResponse {
        val name = individualRepository.findPreferredName(individual.id!!)
        val biography = individualRepository.findBiographyNote(individual.id!!)?.text

        return IndividualResponse(
            id = individual.id.toString(),
            xref = individual.xref!!,
            sex = individual.sex,
            isLiving = individual.isLiving!!,
            givenName = name?.givenName,
            surname = name?.surname,
            biography = biography,
        )
    }

    private fun validateSex(sex: String?) {
        if (sex == null) return
        if (sex !in VALID_SEX) {
            throw BadRequestException("sex must be one of: ${VALID_SEX.joinToString()}")
        }
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
        private val VALID_SEX = setOf("M", "F", "X", "U")

        private fun formatName(
            givenName: String?,
            surname: String?,
            xref: String,
        ): String =
            listOfNotNull(givenName, surname).joinToString(" ").ifBlank { xref }
    }
}
