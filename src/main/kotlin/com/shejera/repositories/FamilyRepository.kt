package com.shejera.repositories

import com.shejera.db.generated.routines.references.nextGedcomXref
import com.shejera.db.generated.tables.records.FamilyChildRecord
import com.shejera.db.generated.tables.records.FamilyEventRecord
import com.shejera.db.generated.tables.records.FamilyRecord
import com.shejera.db.generated.tables.records.FamilySpouseRecord
import com.shejera.db.generated.tables.references.FAMILY
import com.shejera.db.generated.tables.references.FAMILY_CHILD
import com.shejera.db.generated.tables.references.FAMILY_EVENT
import com.shejera.db.generated.tables.references.FAMILY_SPOUSE
import com.shejera.db.generated.tables.references.INDIVIDUAL
import com.shejera.db.generated.tables.references.INDIVIDUAL_NAME
import com.shejera.db.generated.tables.references.PLACE
import org.jooq.DSLContext
import java.time.LocalDate
import java.util.UUID

class FamilyRepository(
    private val dsl: DSLContext,
) {
    fun nextXref(treeId: UUID): String =
        nextGedcomXref(dsl.configuration(), treeId, "F")
            ?: error("Failed to generate family xref")

    fun insert(
        treeId: UUID,
        xref: String,
    ): FamilyRecord =
        dsl.insertInto(FAMILY)
            .set(FAMILY.TREE_ID, treeId)
            .set(FAMILY.XREF, xref)
            .returning()
            .fetchOne()!!

    fun insertSpouse(
        familyId: UUID,
        individualId: UUID,
        role: String,
        sortOrder: Int,
    ): FamilySpouseRecord =
        dsl.insertInto(FAMILY_SPOUSE)
            .set(FAMILY_SPOUSE.FAMILY_ID, familyId)
            .set(FAMILY_SPOUSE.INDIVIDUAL_ID, individualId)
            .set(FAMILY_SPOUSE.ROLE, role)
            .set(FAMILY_SPOUSE.SORT_ORDER, sortOrder)
            .returning()
            .fetchOne()!!

    fun insertChild(
        familyId: UUID,
        individualId: UUID,
        pedigree: String?,
        sortOrder: Int,
    ): FamilyChildRecord =
        dsl.insertInto(FAMILY_CHILD)
            .set(FAMILY_CHILD.FAMILY_ID, familyId)
            .set(FAMILY_CHILD.INDIVIDUAL_ID, individualId)
            .set(FAMILY_CHILD.PEDIGREE, pedigree)
            .set(FAMILY_CHILD.SORT_ORDER, sortOrder)
            .returning()
            .fetchOne()!!

    fun insertEvent(
        familyId: UUID,
        tag: String,
        eventType: String?,
        dateText: String?,
        dateSort: LocalDate?,
        placeId: UUID?,
        description: String?,
        sortOrder: Int = 0,
    ): FamilyEventRecord =
        dsl.insertInto(FAMILY_EVENT)
            .set(FAMILY_EVENT.FAMILY_ID, familyId)
            .set(FAMILY_EVENT.TAG, tag)
            .set(FAMILY_EVENT.EVENT_TYPE, eventType)
            .set(FAMILY_EVENT.DATE_TEXT, dateText)
            .set(FAMILY_EVENT.DATE_SORT, dateSort)
            .set(FAMILY_EVENT.PLACE_ID, placeId)
            .set(FAMILY_EVENT.DESCRIPTION, description)
            .set(FAMILY_EVENT.SORT_ORDER, sortOrder)
            .returning()
            .fetchOne()!!

    fun findById(id: UUID): FamilyRecord? =
        dsl.selectFrom(FAMILY)
            .where(FAMILY.ID.eq(id))
            .fetchOne()

    fun findByIdAndTree(
        id: UUID,
        treeId: UUID,
    ): FamilyRecord? =
        dsl.selectFrom(FAMILY)
            .where(FAMILY.ID.eq(id))
            .and(FAMILY.TREE_ID.eq(treeId))
            .fetchOne()

    fun listByTree(treeId: UUID): List<FamilyRecord> =
        dsl.selectFrom(FAMILY)
            .where(FAMILY.TREE_ID.eq(treeId))
            .orderBy(FAMILY.XREF.asc())
            .fetch()

    fun delete(id: UUID): Boolean =
        dsl.deleteFrom(FAMILY)
            .where(FAMILY.ID.eq(id))
            .execute() > 0

    fun listSpouses(familyId: UUID): List<SpouseRow> =
        dsl.select(
            FAMILY_SPOUSE.ID,
            FAMILY_SPOUSE.INDIVIDUAL_ID,
            FAMILY_SPOUSE.ROLE,
            FAMILY_SPOUSE.SORT_ORDER,
            INDIVIDUAL.XREF,
            INDIVIDUAL_NAME.GIVEN_NAME,
            INDIVIDUAL_NAME.SURNAME,
        )
            .from(FAMILY_SPOUSE)
            .join(INDIVIDUAL).on(INDIVIDUAL.ID.eq(FAMILY_SPOUSE.INDIVIDUAL_ID))
            .leftJoin(INDIVIDUAL_NAME).on(
                INDIVIDUAL_NAME.INDIVIDUAL_ID.eq(INDIVIDUAL.ID)
                    .and(INDIVIDUAL_NAME.IS_PREFERRED.eq(true)),
            )
            .where(FAMILY_SPOUSE.FAMILY_ID.eq(familyId))
            .orderBy(FAMILY_SPOUSE.SORT_ORDER.asc())
            .fetch { record ->
                SpouseRow(
                    individualId = record.get(FAMILY_SPOUSE.INDIVIDUAL_ID)!!,
                    xref = record.get(INDIVIDUAL.XREF)!!,
                    role = record.get(FAMILY_SPOUSE.ROLE)!!,
                    givenName = record.get(INDIVIDUAL_NAME.GIVEN_NAME),
                    surname = record.get(INDIVIDUAL_NAME.SURNAME),
                    sortOrder = record.get(FAMILY_SPOUSE.SORT_ORDER)!!,
                )
            }

    fun listChildren(familyId: UUID): List<ChildRow> =
        dsl.select(
            FAMILY_CHILD.INDIVIDUAL_ID,
            FAMILY_CHILD.PEDIGREE,
            FAMILY_CHILD.SORT_ORDER,
            INDIVIDUAL.XREF,
            INDIVIDUAL_NAME.GIVEN_NAME,
            INDIVIDUAL_NAME.SURNAME,
        )
            .from(FAMILY_CHILD)
            .join(INDIVIDUAL).on(INDIVIDUAL.ID.eq(FAMILY_CHILD.INDIVIDUAL_ID))
            .leftJoin(INDIVIDUAL_NAME).on(
                INDIVIDUAL_NAME.INDIVIDUAL_ID.eq(INDIVIDUAL.ID)
                    .and(INDIVIDUAL_NAME.IS_PREFERRED.eq(true)),
            )
            .where(FAMILY_CHILD.FAMILY_ID.eq(familyId))
            .orderBy(FAMILY_CHILD.SORT_ORDER.asc())
            .fetch { record ->
                ChildRow(
                    individualId = record.get(FAMILY_CHILD.INDIVIDUAL_ID)!!,
                    xref = record.get(INDIVIDUAL.XREF)!!,
                    pedigree = record.get(FAMILY_CHILD.PEDIGREE),
                    givenName = record.get(INDIVIDUAL_NAME.GIVEN_NAME),
                    surname = record.get(INDIVIDUAL_NAME.SURNAME),
                    sortOrder = record.get(FAMILY_CHILD.SORT_ORDER)!!,
                )
            }

    fun listEvents(familyId: UUID): List<EventRow> =
        dsl.select(
            FAMILY_EVENT.ID,
            FAMILY_EVENT.TAG,
            FAMILY_EVENT.EVENT_TYPE,
            FAMILY_EVENT.DATE_TEXT,
            FAMILY_EVENT.DATE_SORT,
            FAMILY_EVENT.DESCRIPTION,
            PLACE.NAME,
        )
            .from(FAMILY_EVENT)
            .leftJoin(PLACE).on(PLACE.ID.eq(FAMILY_EVENT.PLACE_ID))
            .where(FAMILY_EVENT.FAMILY_ID.eq(familyId))
            .orderBy(FAMILY_EVENT.SORT_ORDER.asc(), FAMILY_EVENT.ID.asc())
            .fetch { record ->
                EventRow(
                    id = record.get(FAMILY_EVENT.ID)!!,
                    tag = record.get(FAMILY_EVENT.TAG)!!,
                    eventType = record.get(FAMILY_EVENT.EVENT_TYPE),
                    dateText = record.get(FAMILY_EVENT.DATE_TEXT),
                    dateSort = record.get(FAMILY_EVENT.DATE_SORT),
                    placeName = record.get(PLACE.NAME),
                    description = record.get(FAMILY_EVENT.DESCRIPTION),
                )
            }
}

data class SpouseRow(
    val individualId: UUID,
    val xref: String,
    val role: String,
    val givenName: String?,
    val surname: String?,
    val sortOrder: Int,
)

data class ChildRow(
    val individualId: UUID,
    val xref: String,
    val pedigree: String?,
    val givenName: String?,
    val surname: String?,
    val sortOrder: Int,
)

data class EventRow(
    val id: UUID,
    val tag: String,
    val eventType: String?,
    val dateText: String?,
    val dateSort: LocalDate?,
    val placeName: String?,
    val description: String?,
)
