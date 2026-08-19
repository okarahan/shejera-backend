package com.shejera.services

import com.shejera.api.BadRequestException
import com.shejera.api.ConflictException
import com.shejera.api.NotFoundException
import com.shejera.db.generated.tables.records.IndividualRecord
import com.shejera.gedcom.GedcomTags
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
    fun list(treeId: UUID): List<IndividualResponse> {
        return individualRepository.listByTree(treeId).map { toResponse(it) }
    }

    fun clearTree(treeId: UUID) {
        individualRepository.deleteAllByTree(treeId)
    }

    fun get(id: UUID, treeId: UUID): IndividualResponse {
        val individual =
            individualRepository.findByIdAndTree(id, treeId)
                ?: throw NotFoundException("Individual not found: $id")
        return toResponse(individual)
    }

    fun create(request: CreateIndividualRequest, treeId: UUID): IndividualResponse {
        validateSex(request.sex)
        if (request.givenName.isBlank() && request.surname.isBlank()) {
            throw BadRequestException("givenName or surname is required")
        }


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
                        isLiving =
                            if (!request.deathDate.isNullOrBlank()) {
                                false
                            } else {
                                request.isLiving
                            },
                    )

                txRepo.insertName(
                    individualId = created.id!!,
                    givenName = request.givenName,
                    surname = request.surname,
                )

                if (!request.biography.isNullOrBlank()) {
                    txRepo.insertBiographyNote(treeId, created.id!!, request.biography)
                }

                if (!request.birthDate.isNullOrBlank() || !request.birthPlace.isNullOrBlank()) {
                    upsertBirthEvent(
                        txDsl,
                        created.id!!,
                        birthDate = request.birthDate,
                        birthPlace = request.birthPlace,
                        updateDate = true,
                        updatePlace = true,
                    )
                }

                if (!request.deathDate.isNullOrBlank()) {
                    upsertDeathEvent(txDsl, created.id!!, request.deathDate)
                }

                created
            }

        return toResponse(individual)
    }

    fun update(
        id: UUID,
        request: UpdateIndividualRequest,
        treeId: UUID,
    ): IndividualResponse {
        validateSex(request.sex)

        val existing =
            individualRepository.findByIdAndTree(id, treeId)
                ?: throw NotFoundException("Individual not found: $id")

        dsl.transaction { ctx ->
            val txDsl = ctx.dsl()
            val txRepo = IndividualRepository(txDsl)
            var resolvedIsLiving = request.isLiving ?: existing.isLiving!!

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

            if (request.birthDate != null || request.birthPlace != null) {
                upsertBirthEvent(
                    txDsl,
                    id,
                    birthDate = request.birthDate,
                    birthPlace = request.birthPlace,
                    updateDate = request.birthDate != null,
                    updatePlace = request.birthPlace != null,
                )
            }

            if (request.deathDate != null) {
                upsertDeathEvent(txDsl, id, request.deathDate)
                if (request.deathDate.isNotBlank()) {
                    resolvedIsLiving = false
                }
            }

            if (request.sex != null || request.isLiving != null || request.deathDate != null) {
                txRepo.update(
                    id = id,
                    sex = request.sex ?: existing.sex,
                    isLiving = resolvedIsLiving,
                )
            }
        }

        return get(id, treeId)
    }

    fun getRelationships(id: UUID, treeId: UUID): IndividualRelationshipsResponse {
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

    fun delete(id: UUID, treeId: UUID) {
        individualRepository.findByIdAndTree(id, treeId)
            ?: throw NotFoundException("Individual not found: $id")

        val relationships = getRelationships(id, treeId)
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
        treeId: UUID,
    ): IndividualEventResponse {
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

    fun listEvents(id: UUID, treeId: UUID): List<IndividualEventResponse> {
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
        val birth = eventRepository.findBirthEvent(individual.id!!)
        val deathDate = eventRepository.findDeathEvent(individual.id!!)?.dateText

        return IndividualResponse(
            id = individual.id.toString(),
            xref = individual.xref!!,
            sex = individual.sex,
            isLiving = individual.isLiving!!,
            givenName = name?.givenName,
            surname = name?.surname,
            biography = biography,
            birthDate = birth?.dateText,
            deathDate = deathDate,
            birthPlace = birth?.placeName,
        )
    }

    private fun upsertDeathEvent(
        dsl: DSLContext,
        individualId: UUID,
        deathDate: String,
    ) {
        val eventRepo = EventRepository(dsl)
        val existing = eventRepo.findDeathEvent(individualId)

        if (deathDate.isBlank()) {
            if (existing != null) {
                eventRepo.deleteIndividualEvent(existing.id)
            }
            return
        }

        val (dateText, dateSort) = parsePersonDate(deathDate, "deathDate")

        if (existing != null) {
            eventRepo.updateIndividualEvent(existing.id, dateText, dateSort)
        } else {
            eventRepo.insertIndividualEvent(
                individualId = individualId,
                tag = GedcomTags.DEAT,
                eventType = null,
                dateText = dateText,
                dateSort = dateSort,
                placeId = null,
                description = null,
            )
        }
    }

    private fun upsertBirthEvent(
        dsl: DSLContext,
        individualId: UUID,
        birthDate: String?,
        birthPlace: String?,
        updateDate: Boolean,
        updatePlace: Boolean,
    ) {
        val eventRepo = EventRepository(dsl)
        val existing = eventRepo.findBirthEvent(individualId)

        val (dateText, dateSort) =
            when {
                !updateDate -> existing?.dateText to existing?.dateSort
                birthDate.isNullOrBlank() -> null to null
                else -> parsePersonDate(birthDate, "birthDate")
            }
        val placeName =
            when {
                !updatePlace -> existing?.placeName?.trim()?.takeIf { it.isNotEmpty() }
                birthPlace.isNullOrBlank() -> null
                else -> birthPlace.trim()
            }

        if (dateText.isNullOrBlank() && placeName.isNullOrBlank()) {
            if (existing != null) {
                eventRepo.deleteIndividualEvent(existing.id)
            }
            return
        }

        val placeId = placeName?.let { PlaceRepository(dsl).findOrCreate(it).id }

        if (existing != null) {
            eventRepo.updateIndividualEvent(
                existing.id,
                dateText,
                dateSort,
                placeId = placeId,
                updatePlace = true,
            )
        } else {
            eventRepo.insertIndividualEvent(
                individualId = individualId,
                tag = GedcomTags.BIRT,
                eventType = null,
                dateText = dateText,
                dateSort = dateSort,
                placeId = placeId,
                description = null,
            )
        }
    }

    private fun parsePersonDate(value: String, fieldName: String): Pair<String, LocalDate?> {
        val trimmed = value.trim()
        if (YEAR_ONLY.matches(trimmed)) {
            val year = trimmed.toInt()
            if (year !in 1..9999) {
                throw BadRequestException("$fieldName year must be between 1 and 9999")
            }
            return trimmed to null
        }

        val match = DAY_MONTH_YEAR.matchEntire(trimmed)
            ?: throw BadRequestException("$fieldName must be DD.MM.YYYY or YYYY")

        val day = match.groupValues[1].toInt()
        val month = match.groupValues[2].toInt()
        val year = match.groupValues[3].toInt()

        return try {
            trimmed to LocalDate.of(year, month, day)
        } catch (_: DateTimeParseException) {
            throw BadRequestException("$fieldName is not a valid calendar date")
        }
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
        private val YEAR_ONLY = Regex("^\\d{4}$")
        private val DAY_MONTH_YEAR = Regex("^(\\d{2})\\.(\\d{2})\\.(\\d{4})$")

        private fun formatName(
            givenName: String?,
            surname: String?,
            xref: String,
        ): String =
            listOfNotNull(givenName, surname).joinToString(" ").ifBlank { xref }
    }
}
