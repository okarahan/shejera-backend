package com.shejera.repositories

import com.shejera.db.generated.tables.records.PlaceRecord
import com.shejera.db.generated.tables.references.PLACE
import org.jooq.DSLContext
import java.util.UUID

class PlaceRepository(
    private val dsl: DSLContext,
) {
    fun findByName(name: String): PlaceRecord? =
        dsl.selectFrom(PLACE)
            .where(PLACE.NAME.eq(name))
            .fetchOne()

    fun create(name: String): PlaceRecord =
        dsl.insertInto(PLACE)
            .set(PLACE.NAME, name)
            .returning()
            .fetchOne()!!

    fun findOrCreate(name: String): PlaceRecord = findByName(name) ?: create(name)

    fun findById(id: UUID): PlaceRecord? =
        dsl.selectFrom(PLACE)
            .where(PLACE.ID.eq(id))
            .fetchOne()
}
