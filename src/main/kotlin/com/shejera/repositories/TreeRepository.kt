package com.shejera.repositories

import com.shejera.api.NotFoundException
import com.shejera.db.generated.tables.records.GedcomTreeRecord
import com.shejera.db.generated.tables.references.GEDCOM_TREE
import org.jooq.DSLContext
import java.util.UUID

class TreeRepository(
    private val dsl: DSLContext,
) {
    fun getDefaultTree(): GedcomTreeRecord =
        dsl.selectFrom(GEDCOM_TREE)
            .orderBy(GEDCOM_TREE.CREATED_AT.asc())
            .limit(1)
            .fetchOne()
            ?: throw NotFoundException("No gedcom tree configured")

    fun getDefaultTreeId(): UUID = getDefaultTree().id!!
}
