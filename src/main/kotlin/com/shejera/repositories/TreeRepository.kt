package com.shejera.repositories

import com.shejera.api.NotFoundException
import com.shejera.db.AppUserTable
import com.shejera.db.GedcomTreeAuthFields
import com.shejera.db.TreeMetaRow
import com.shejera.db.generated.tables.records.GedcomTreeRecord
import com.shejera.db.generated.tables.references.GEDCOM_TREE
import org.jooq.DSLContext
import org.jooq.impl.DSL
import java.time.OffsetDateTime
import java.util.UUID

class TreeRepository(
    private val dsl: DSLContext,
) {
    fun getMainTree(): TreeMetaRow =
        findMainTree() ?: throw NotFoundException("No main gedcom tree configured")

    fun getMainTreeId(): UUID = getMainTree().id

    /** @deprecated Prefer [getMainTreeId] — kept for gradual migration. */
    fun getDefaultTreeId(): UUID = getMainTreeId()

    fun getDefaultTree(): GedcomTreeRecord =
        dsl.selectFrom(GEDCOM_TREE)
            .where(GedcomTreeAuthFields.KIND.eq("main"))
            .orderBy(GEDCOM_TREE.CREATED_AT.asc())
            .limit(1)
            .fetchOne()
            ?: throw NotFoundException("No gedcom tree configured")

    fun findById(id: UUID): TreeMetaRow? =
        dsl
            .select(
                GEDCOM_TREE.ID,
                GEDCOM_TREE.NAME,
                GedcomTreeAuthFields.KIND,
                GedcomTreeAuthFields.STATUS,
                GedcomTreeAuthFields.CREATED_BY_USER_ID,
                GedcomTreeAuthFields.CONTRIBUTOR_USER_ID,
                GedcomTreeAuthFields.INVITE_ID,
                GedcomTreeAuthFields.EXPIRES_AT,
                GEDCOM_TREE.CREATED_AT,
                GEDCOM_TREE.UPDATED_AT,
            ).from(GEDCOM_TREE)
            .where(GEDCOM_TREE.ID.eq(id))
            .fetchOne()
            ?.toTreeMeta()

    fun findMainTree(): TreeMetaRow? =
        dsl
            .select(
                GEDCOM_TREE.ID,
                GEDCOM_TREE.NAME,
                GedcomTreeAuthFields.KIND,
                GedcomTreeAuthFields.STATUS,
                GedcomTreeAuthFields.CREATED_BY_USER_ID,
                GedcomTreeAuthFields.CONTRIBUTOR_USER_ID,
                GedcomTreeAuthFields.INVITE_ID,
                GedcomTreeAuthFields.EXPIRES_AT,
                GEDCOM_TREE.CREATED_AT,
                GEDCOM_TREE.UPDATED_AT,
            ).from(GEDCOM_TREE)
            .where(GedcomTreeAuthFields.KIND.eq("main"))
            .orderBy(GEDCOM_TREE.CREATED_AT.asc())
            .limit(1)
            .fetchOne()
            ?.toTreeMeta()

    fun findActiveContributionForUser(userId: UUID): TreeMetaRow? =
        dsl
            .select(
                GEDCOM_TREE.ID,
                GEDCOM_TREE.NAME,
                GedcomTreeAuthFields.KIND,
                GedcomTreeAuthFields.STATUS,
                GedcomTreeAuthFields.CREATED_BY_USER_ID,
                GedcomTreeAuthFields.CONTRIBUTOR_USER_ID,
                GedcomTreeAuthFields.INVITE_ID,
                GedcomTreeAuthFields.EXPIRES_AT,
                GEDCOM_TREE.CREATED_AT,
                GEDCOM_TREE.UPDATED_AT,
            ).from(GEDCOM_TREE)
            .where(
                GedcomTreeAuthFields.KIND.eq("contribution"),
                GedcomTreeAuthFields.CONTRIBUTOR_USER_ID.eq(userId),
                GedcomTreeAuthFields.STATUS.`in`("draft", "submitted"),
            ).orderBy(GEDCOM_TREE.CREATED_AT.desc())
            .limit(1)
            .fetchOne()
            ?.toTreeMeta()

    fun listAccessible(
        userId: UUID,
        isAdmin: Boolean,
    ): List<TreeMetaRow> {
        val statusFilter =
            if (isAdmin) {
                GedcomTreeAuthFields.STATUS.`in`("draft", "submitted", "merged")
            } else {
                GedcomTreeAuthFields.STATUS.`in`("draft", "submitted")
            }

        val contributionCondition =
            DSL.and(
                GedcomTreeAuthFields.KIND.eq("contribution"),
                GedcomTreeAuthFields.CONTRIBUTOR_USER_ID.isNotNull,
                GedcomTreeAuthFields.CONTRIBUTOR_USER_ID.ne(userId),
                AppUserTable.ROLE.ne("admin"),
                statusFilter,
            )

        val condition =
            if (isAdmin) {
                DSL.or(
                    GedcomTreeAuthFields.KIND.eq("main"),
                    DSL.and(
                        GedcomTreeAuthFields.KIND.eq("contribution"),
                        GedcomTreeAuthFields.CONTRIBUTOR_USER_ID.isNotNull,
                        AppUserTable.ROLE.ne("admin"),
                        statusFilter,
                    ),
                )
            } else {
                DSL.or(
                    GedcomTreeAuthFields.KIND.eq("main"),
                    DSL.and(
                        GedcomTreeAuthFields.KIND.eq("contribution"),
                        GedcomTreeAuthFields.CONTRIBUTOR_USER_ID.eq(userId),
                        statusFilter,
                    ),
                )
            }

        return dsl
            .select(
                GEDCOM_TREE.ID,
                GEDCOM_TREE.NAME,
                GedcomTreeAuthFields.KIND,
                GedcomTreeAuthFields.STATUS,
                GedcomTreeAuthFields.CREATED_BY_USER_ID,
                GedcomTreeAuthFields.CONTRIBUTOR_USER_ID,
                GedcomTreeAuthFields.INVITE_ID,
                GedcomTreeAuthFields.EXPIRES_AT,
                GEDCOM_TREE.CREATED_AT,
                GEDCOM_TREE.UPDATED_AT,
            ).from(GEDCOM_TREE)
            .leftJoin(AppUserTable.TABLE)
            .on(GedcomTreeAuthFields.CONTRIBUTOR_USER_ID.eq(AppUserTable.ID))
            .where(condition)
            .orderBy(
                GedcomTreeAuthFields.KIND.asc(),
                GEDCOM_TREE.CREATED_AT.desc(),
            ).fetch()
            .map { it.toTreeMeta() }
    }

    fun createContribution(
        name: String,
        createdByUserId: UUID,
        contributorUserId: UUID?,
        inviteId: UUID?,
        expiresAt: OffsetDateTime?,
    ): TreeMetaRow {
        val id = UUID.randomUUID()
        val now = OffsetDateTime.now()
        dsl
            .insertInto(GEDCOM_TREE)
            .set(GEDCOM_TREE.ID, id)
            .set(GEDCOM_TREE.NAME, name.trim())
            .set(GEDCOM_TREE.GEDCOM_VERSION, "7.0")
            .set(GEDCOM_TREE.CREATED_AT, now)
            .set(GEDCOM_TREE.UPDATED_AT, now)
            .set(GedcomTreeAuthFields.KIND, "contribution")
            .set(GedcomTreeAuthFields.STATUS, "draft")
            .set(GedcomTreeAuthFields.CREATED_BY_USER_ID, createdByUserId)
            .set(GedcomTreeAuthFields.CONTRIBUTOR_USER_ID, contributorUserId)
            .set(GedcomTreeAuthFields.INVITE_ID, inviteId)
            .set(GedcomTreeAuthFields.EXPIRES_AT, expiresAt)
            .execute()
        return findById(id)!!
    }

    fun updateStatus(
        id: UUID,
        status: String,
    ): Boolean =
        dsl
            .update(GEDCOM_TREE)
            .set(GedcomTreeAuthFields.STATUS, status)
            .set(GEDCOM_TREE.UPDATED_AT, OffsetDateTime.now())
            .where(GEDCOM_TREE.ID.eq(id))
            .execute() == 1

    fun delete(id: UUID): Boolean =
        dsl
            .deleteFrom(GEDCOM_TREE)
            .where(
                GEDCOM_TREE.ID.eq(id),
                GedcomTreeAuthFields.KIND.eq("contribution"),
            ).execute() == 1

    private fun org.jooq.Record.toTreeMeta() =
        TreeMetaRow(
            id = get(GEDCOM_TREE.ID)!!,
            name = get(GEDCOM_TREE.NAME)!!,
            kind = get(GedcomTreeAuthFields.KIND)!!,
            status = get(GedcomTreeAuthFields.STATUS),
            createdByUserId = get(GedcomTreeAuthFields.CREATED_BY_USER_ID),
            contributorUserId = get(GedcomTreeAuthFields.CONTRIBUTOR_USER_ID),
            inviteId = get(GedcomTreeAuthFields.INVITE_ID),
            expiresAt = get(GedcomTreeAuthFields.EXPIRES_AT),
            createdAt = get(GEDCOM_TREE.CREATED_AT)!!,
            updatedAt = get(GEDCOM_TREE.UPDATED_AT)!!,
        )
}
