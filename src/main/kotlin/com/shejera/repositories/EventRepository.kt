package com.shejera.repositories

import com.shejera.db.generated.tables.records.IndividualEventRecord
import com.shejera.db.generated.tables.references.INDIVIDUAL_EVENT
import com.shejera.db.generated.tables.references.PLACE
import org.jooq.DSLContext
import java.time.LocalDate
import java.util.UUID

class EventRepository(
    private val dsl: DSLContext,
) {
    fun insertIndividualEvent(
        individualId: UUID,
        tag: String,
        eventType: String?,
        dateText: String?,
        dateSort: LocalDate?,
        placeId: UUID?,
        description: String?,
        sortOrder: Int = 0,
    ): IndividualEventRecord =
        dsl.insertInto(INDIVIDUAL_EVENT)
            .set(INDIVIDUAL_EVENT.INDIVIDUAL_ID, individualId)
            .set(INDIVIDUAL_EVENT.TAG, tag)
            .set(INDIVIDUAL_EVENT.EVENT_TYPE, eventType)
            .set(INDIVIDUAL_EVENT.DATE_TEXT, dateText)
            .set(INDIVIDUAL_EVENT.DATE_SORT, dateSort)
            .set(INDIVIDUAL_EVENT.PLACE_ID, placeId)
            .set(INDIVIDUAL_EVENT.DESCRIPTION, description)
            .set(INDIVIDUAL_EVENT.SORT_ORDER, sortOrder)
            .returning()
            .fetchOne()!!

    fun findDeathEvent(individualId: UUID): IndividualEventRow? =
        findEventByTag(individualId, "DEAT")

    fun findBirthEvent(individualId: UUID): IndividualEventRow? =
        findEventByTag(individualId, "BIRT")

    private fun findEventByTag(individualId: UUID, tag: String): IndividualEventRow? =
        dsl.select(
            INDIVIDUAL_EVENT.ID,
            INDIVIDUAL_EVENT.TAG,
            INDIVIDUAL_EVENT.EVENT_TYPE,
            INDIVIDUAL_EVENT.DATE_TEXT,
            INDIVIDUAL_EVENT.DATE_SORT,
            INDIVIDUAL_EVENT.DESCRIPTION,
            PLACE.NAME,
        )
            .from(INDIVIDUAL_EVENT)
            .leftJoin(PLACE).on(PLACE.ID.eq(INDIVIDUAL_EVENT.PLACE_ID))
            .where(INDIVIDUAL_EVENT.INDIVIDUAL_ID.eq(individualId))
            .and(INDIVIDUAL_EVENT.TAG.eq(tag))
            .orderBy(INDIVIDUAL_EVENT.SORT_ORDER.asc(), INDIVIDUAL_EVENT.ID.asc())
            .limit(1)
            .fetchOne { record ->
                IndividualEventRow(
                    id = record.get(INDIVIDUAL_EVENT.ID)!!,
                    tag = record.get(INDIVIDUAL_EVENT.TAG)!!,
                    eventType = record.get(INDIVIDUAL_EVENT.EVENT_TYPE),
                    dateText = record.get(INDIVIDUAL_EVENT.DATE_TEXT),
                    dateSort = record.get(INDIVIDUAL_EVENT.DATE_SORT),
                    placeName = record.get(PLACE.NAME),
                    description = record.get(INDIVIDUAL_EVENT.DESCRIPTION),
                )
            }

    fun updateIndividualEvent(
        id: UUID,
        dateText: String?,
        dateSort: LocalDate?,
    ): IndividualEventRecord =
        dsl.update(INDIVIDUAL_EVENT)
            .set(INDIVIDUAL_EVENT.DATE_TEXT, dateText)
            .set(INDIVIDUAL_EVENT.DATE_SORT, dateSort)
            .where(INDIVIDUAL_EVENT.ID.eq(id))
            .returning()
            .fetchOne()!!

    fun deleteIndividualEvent(id: UUID): Boolean =
        dsl.deleteFrom(INDIVIDUAL_EVENT)
            .where(INDIVIDUAL_EVENT.ID.eq(id))
            .execute() > 0

    fun listIndividualEvents(individualId: UUID): List<IndividualEventRow> =
        dsl.select(
            INDIVIDUAL_EVENT.ID,
            INDIVIDUAL_EVENT.TAG,
            INDIVIDUAL_EVENT.EVENT_TYPE,
            INDIVIDUAL_EVENT.DATE_TEXT,
            INDIVIDUAL_EVENT.DATE_SORT,
            INDIVIDUAL_EVENT.DESCRIPTION,
            PLACE.NAME,
        )
            .from(INDIVIDUAL_EVENT)
            .leftJoin(PLACE).on(PLACE.ID.eq(INDIVIDUAL_EVENT.PLACE_ID))
            .where(INDIVIDUAL_EVENT.INDIVIDUAL_ID.eq(individualId))
            .orderBy(INDIVIDUAL_EVENT.SORT_ORDER.asc(), INDIVIDUAL_EVENT.ID.asc())
            .fetch { record ->
                IndividualEventRow(
                    id = record.get(INDIVIDUAL_EVENT.ID)!!,
                    tag = record.get(INDIVIDUAL_EVENT.TAG)!!,
                    eventType = record.get(INDIVIDUAL_EVENT.EVENT_TYPE),
                    dateText = record.get(INDIVIDUAL_EVENT.DATE_TEXT),
                    dateSort = record.get(INDIVIDUAL_EVENT.DATE_SORT),
                    placeName = record.get(PLACE.NAME),
                    description = record.get(INDIVIDUAL_EVENT.DESCRIPTION),
                )
            }
}

data class IndividualEventRow(
    val id: UUID,
    val tag: String,
    val eventType: String?,
    val dateText: String?,
    val dateSort: LocalDate?,
    val placeName: String?,
    val description: String?,
)
