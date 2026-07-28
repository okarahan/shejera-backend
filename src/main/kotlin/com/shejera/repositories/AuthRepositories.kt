package com.shejera.repositories

import com.shejera.db.AppSessionTable
import com.shejera.db.AppUserRow
import com.shejera.db.AppUserTable
import com.shejera.db.InviteRow
import com.shejera.db.InviteTable
import org.jooq.DSLContext
import org.jooq.impl.DSL
import java.time.OffsetDateTime
import java.util.UUID

class UserRepository(
    private val dsl: DSLContext,
) {
    fun findById(id: UUID): AppUserRow? =
        dsl
            .select(
                AppUserTable.ID,
                AppUserTable.EMAIL,
                AppUserTable.DISPLAY_NAME,
                AppUserTable.ROLE,
                AppUserTable.CREATED_AT,
            ).from(AppUserTable.TABLE)
            .where(AppUserTable.ID.eq(id))
            .fetchOne()
            ?.let {
                AppUserRow(
                    id = it.get(AppUserTable.ID)!!,
                    email = it.get(AppUserTable.EMAIL)!!,
                    displayName = it.get(AppUserTable.DISPLAY_NAME)!!,
                    role = it.get(AppUserTable.ROLE)!!,
                    createdAt = it.get(AppUserTable.CREATED_AT)!!,
                )
            }

    fun findByEmail(email: String): AppUserRow? =
        dsl
            .select(
                AppUserTable.ID,
                AppUserTable.EMAIL,
                AppUserTable.DISPLAY_NAME,
                AppUserTable.ROLE,
                AppUserTable.CREATED_AT,
            ).from(AppUserTable.TABLE)
            .where(DSL.lower(AppUserTable.EMAIL).eq(email.lowercase()))
            .fetchOne()
            ?.let {
                AppUserRow(
                    id = it.get(AppUserTable.ID)!!,
                    email = it.get(AppUserTable.EMAIL)!!,
                    displayName = it.get(AppUserTable.DISPLAY_NAME)!!,
                    role = it.get(AppUserTable.ROLE)!!,
                    createdAt = it.get(AppUserTable.CREATED_AT)!!,
                )
            }

    fun countAdmins(): Int =
        dsl.fetchCount(
            AppUserTable.TABLE,
            AppUserTable.ROLE.eq("admin"),
        )

    fun insert(
        email: String,
        displayName: String,
        role: String,
    ): AppUserRow {
        val id = UUID.randomUUID()
        val createdAt = OffsetDateTime.now()
        dsl
            .insertInto(AppUserTable.TABLE)
            .set(AppUserTable.ID, id)
            .set(AppUserTable.EMAIL, email.trim())
            .set(AppUserTable.DISPLAY_NAME, displayName.trim())
            .set(AppUserTable.ROLE, role)
            .set(AppUserTable.CREATED_AT, createdAt)
            .execute()
        return AppUserRow(id, email.trim(), displayName.trim(), role, createdAt)
    }
}

class InviteRepository(
    private val dsl: DSLContext,
) {
    fun findByTokenHash(tokenHash: String): InviteRow? =
        dsl
            .selectFromInvite()
            .where(InviteTable.TOKEN_HASH.eq(tokenHash))
            .fetchOne()
            ?.toInviteRow()

    fun findById(id: UUID): InviteRow? =
        dsl
            .selectFromInvite()
            .where(InviteTable.ID.eq(id))
            .fetchOne()
            ?.toInviteRow()

    fun listAll(): List<InviteRow> =
        dsl
            .selectFromInvite()
            .orderBy(InviteTable.CREATED_AT.desc())
            .fetch()
            .map { it.toInviteRow() }

    fun insert(
        tokenHash: String,
        email: String,
        displayName: String,
        role: String,
        createdByUserId: UUID?,
        expiresAt: OffsetDateTime?,
    ): InviteRow {
        val id = UUID.randomUUID()
        val createdAt = OffsetDateTime.now()
        dsl
            .insertInto(InviteTable.TABLE)
            .set(InviteTable.ID, id)
            .set(InviteTable.TOKEN_HASH, tokenHash)
            .set(InviteTable.EMAIL, email.trim())
            .set(InviteTable.DISPLAY_NAME, displayName.trim())
            .set(InviteTable.ROLE, role)
            .set(InviteTable.STATUS, "pending")
            .set(InviteTable.CREATED_BY_USER_ID, createdByUserId)
            .set(InviteTable.EXPIRES_AT, expiresAt)
            .set(InviteTable.CREATED_AT, createdAt)
            .execute()
        return InviteRow(
            id = id,
            tokenHash = tokenHash,
            email = email.trim(),
            displayName = displayName.trim(),
            role = role,
            status = "pending",
            createdByUserId = createdByUserId,
            redeemedByUserId = null,
            expiresAt = expiresAt,
            createdAt = createdAt,
            redeemedAt = null,
        )
    }

    fun markRedeemed(
        id: UUID,
        userId: UUID,
    ) {
        dsl
            .update(InviteTable.TABLE)
            .set(InviteTable.STATUS, "redeemed")
            .set(InviteTable.REDEEMED_BY_USER_ID, userId)
            .set(InviteTable.REDEEMED_AT, OffsetDateTime.now())
            .where(InviteTable.ID.eq(id))
            .execute()
    }

    fun revoke(id: UUID): Boolean =
        dsl
            .update(InviteTable.TABLE)
            .set(InviteTable.STATUS, "revoked")
            .where(InviteTable.ID.eq(id), InviteTable.STATUS.eq("pending"))
            .execute() == 1

    private fun DSLContext.selectFromInvite() =
        select(
            InviteTable.ID,
            InviteTable.TOKEN_HASH,
            InviteTable.EMAIL,
            InviteTable.DISPLAY_NAME,
            InviteTable.ROLE,
            InviteTable.STATUS,
            InviteTable.CREATED_BY_USER_ID,
            InviteTable.REDEEMED_BY_USER_ID,
            InviteTable.EXPIRES_AT,
            InviteTable.CREATED_AT,
            InviteTable.REDEEMED_AT,
        ).from(InviteTable.TABLE)

    private fun org.jooq.Record.toInviteRow() =
        InviteRow(
            id = get(InviteTable.ID)!!,
            tokenHash = get(InviteTable.TOKEN_HASH)!!,
            email = get(InviteTable.EMAIL)!!,
            displayName = get(InviteTable.DISPLAY_NAME)!!,
            role = get(InviteTable.ROLE)!!,
            status = get(InviteTable.STATUS)!!,
            createdByUserId = get(InviteTable.CREATED_BY_USER_ID),
            redeemedByUserId = get(InviteTable.REDEEMED_BY_USER_ID),
            expiresAt = get(InviteTable.EXPIRES_AT),
            createdAt = get(InviteTable.CREATED_AT)!!,
            redeemedAt = get(InviteTable.REDEEMED_AT),
        )
}

class SessionRepository(
    private val dsl: DSLContext,
) {
    fun create(
        tokenHash: String,
        userId: UUID,
        expiresAt: OffsetDateTime,
    ): UUID {
        val id = UUID.randomUUID()
        dsl
            .insertInto(AppSessionTable.TABLE)
            .set(AppSessionTable.ID, id)
            .set(AppSessionTable.TOKEN_HASH, tokenHash)
            .set(AppSessionTable.USER_ID, userId)
            .set(AppSessionTable.EXPIRES_AT, expiresAt)
            .set(AppSessionTable.CREATED_AT, OffsetDateTime.now())
            .execute()
        return id
    }

    fun findValidUserId(tokenHash: String): UUID? {
        val now = OffsetDateTime.now()
        return dsl
            .select(AppSessionTable.USER_ID)
            .from(AppSessionTable.TABLE)
            .where(
                AppSessionTable.TOKEN_HASH.eq(tokenHash),
                AppSessionTable.EXPIRES_AT.gt(now),
            ).fetchOne(AppSessionTable.USER_ID)
    }

    fun deleteByTokenHash(tokenHash: String) {
        dsl
            .deleteFrom(AppSessionTable.TABLE)
            .where(AppSessionTable.TOKEN_HASH.eq(tokenHash))
            .execute()
    }
}
