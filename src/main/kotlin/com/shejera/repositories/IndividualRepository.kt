package com.shejera.repositories

import com.shejera.db.generated.routines.references.nextGedcomXref
import com.shejera.db.generated.tables.records.IndividualNameRecord
import com.shejera.db.generated.tables.records.IndividualRecord
import com.shejera.db.generated.tables.records.NoteLinkRecord
import com.shejera.db.generated.tables.records.NoteRecord
import com.shejera.db.generated.tables.references.INDIVIDUAL
import com.shejera.db.generated.tables.references.INDIVIDUAL_NAME
import com.shejera.db.generated.tables.references.NOTE
import com.shejera.db.generated.tables.references.NOTE_LINK
import org.jooq.DSLContext
import java.time.OffsetDateTime
import java.util.UUID

class IndividualRepository(
    private val dsl: DSLContext,
) {
    fun nextXref(treeId: UUID): String =
        nextGedcomXref(dsl.configuration(), treeId, "I")
            ?: error("Failed to generate individual xref")

    fun insert(
        treeId: UUID,
        xref: String,
        sex: String?,
        isLiving: Boolean,
    ): IndividualRecord =
        dsl.insertInto(INDIVIDUAL)
            .set(INDIVIDUAL.TREE_ID, treeId)
            .set(INDIVIDUAL.XREF, xref)
            .set(INDIVIDUAL.SEX, sex)
            .set(INDIVIDUAL.IS_LIVING, isLiving)
            .returning()
            .fetchOne()!!

    fun insertName(
        individualId: UUID,
        givenName: String?,
        surname: String?,
        isPreferred: Boolean = true,
        sortOrder: Int = 0,
    ): IndividualNameRecord {
        val nameFull =
            listOfNotNull(givenName, surname?.let { "/$it/" })
                .joinToString(" ")
                .ifBlank { null }

        return dsl.insertInto(INDIVIDUAL_NAME)
            .set(INDIVIDUAL_NAME.INDIVIDUAL_ID, individualId)
            .set(INDIVIDUAL_NAME.GIVEN_NAME, givenName)
            .set(INDIVIDUAL_NAME.SURNAME, surname)
            .set(INDIVIDUAL_NAME.NAME_FULL, nameFull)
            .set(INDIVIDUAL_NAME.IS_PREFERRED, isPreferred)
            .set(INDIVIDUAL_NAME.SORT_ORDER, sortOrder)
            .returning()
            .fetchOne()!!
    }

    fun updatePreferredName(
        individualId: UUID,
        givenName: String?,
        surname: String?,
    ) {
        val updated =
            dsl.update(INDIVIDUAL_NAME)
                .set(INDIVIDUAL_NAME.GIVEN_NAME, givenName)
                .set(INDIVIDUAL_NAME.SURNAME, surname)
                .set(
                    INDIVIDUAL_NAME.NAME_FULL,
                    listOfNotNull(givenName, surname?.let { "/$it/" })
                        .joinToString(" ")
                        .ifBlank { null },
                )
                .where(INDIVIDUAL_NAME.INDIVIDUAL_ID.eq(individualId))
                .and(INDIVIDUAL_NAME.IS_PREFERRED.eq(true))
                .execute()

        if (updated == 0 && (givenName != null || surname != null)) {
            insertName(individualId, givenName, surname, isPreferred = true)
        }
    }

    fun findById(id: UUID): IndividualRecord? =
        dsl.selectFrom(INDIVIDUAL)
            .where(INDIVIDUAL.ID.eq(id))
            .fetchOne()

    fun findByIdAndTree(
        id: UUID,
        treeId: UUID,
    ): IndividualRecord? =
        dsl.selectFrom(INDIVIDUAL)
            .where(INDIVIDUAL.ID.eq(id))
            .and(INDIVIDUAL.TREE_ID.eq(treeId))
            .fetchOne()

    fun listByTree(treeId: UUID): List<IndividualRecord> =
        dsl.selectFrom(INDIVIDUAL)
            .where(INDIVIDUAL.TREE_ID.eq(treeId))
            .orderBy(INDIVIDUAL.XREF.asc())
            .fetch()

    fun update(
        id: UUID,
        sex: String?,
        isLiving: Boolean,
    ): IndividualRecord? =
        dsl.update(INDIVIDUAL)
            .set(INDIVIDUAL.SEX, sex)
            .set(INDIVIDUAL.IS_LIVING, isLiving)
            .set(INDIVIDUAL.UPDATED_AT, OffsetDateTime.now())
            .where(INDIVIDUAL.ID.eq(id))
            .returning()
            .fetchOne()

    fun delete(id: UUID): Boolean =
        dsl.deleteFrom(INDIVIDUAL)
            .where(INDIVIDUAL.ID.eq(id))
            .execute() > 0

    fun findPreferredName(individualId: UUID): IndividualNameRecord? =
        dsl.selectFrom(INDIVIDUAL_NAME)
            .where(INDIVIDUAL_NAME.INDIVIDUAL_ID.eq(individualId))
            .orderBy(INDIVIDUAL_NAME.IS_PREFERRED.desc(), INDIVIDUAL_NAME.SORT_ORDER.asc())
            .limit(1)
            .fetchOne()

    fun insertBiographyNote(
        treeId: UUID,
        individualId: UUID,
        text: String,
    ): NoteRecord {
        val note =
            dsl.insertInto(NOTE)
                .set(NOTE.TREE_ID, treeId)
                .set(NOTE.TEXT, text)
                .set(NOTE.IS_SHARED, false)
                .returning()
                .fetchOne()!!

        dsl.insertInto(NOTE_LINK)
            .set(NOTE_LINK.NOTE_ID, note.id)
            .set(NOTE_LINK.ENTITY_TYPE, "individual")
            .set(NOTE_LINK.ENTITY_ID, individualId)
            .set(NOTE_LINK.NOTE_TYPE, "biography")
            .execute()

        return note
    }

    fun findBiographyNote(individualId: UUID): NoteRecord? =
        dsl.select(NOTE.asterisk())
            .from(NOTE_LINK)
            .join(NOTE).on(NOTE.ID.eq(NOTE_LINK.NOTE_ID))
            .where(NOTE_LINK.ENTITY_TYPE.eq("individual"))
            .and(NOTE_LINK.ENTITY_ID.eq(individualId))
            .and(NOTE_LINK.NOTE_TYPE.eq("biography"))
            .orderBy(NOTE.CREATED_AT.desc())
            .limit(1)
            .fetchOneInto(NoteRecord::class.java)

    fun findBiographyLink(individualId: UUID): NoteLinkRecord? =
        dsl.selectFrom(NOTE_LINK)
            .where(NOTE_LINK.ENTITY_TYPE.eq("individual"))
            .and(NOTE_LINK.ENTITY_ID.eq(individualId))
            .and(NOTE_LINK.NOTE_TYPE.eq("biography"))
            .orderBy(NOTE_LINK.ID.desc())
            .limit(1)
            .fetchOne()

    fun updateBiographyNote(
        noteId: UUID,
        text: String,
    ) {
        dsl.update(NOTE)
            .set(NOTE.TEXT, text)
            .set(NOTE.UPDATED_AT, OffsetDateTime.now())
            .where(NOTE.ID.eq(noteId))
            .execute()
    }
}
