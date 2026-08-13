package com.shejera.db

import org.jooq.impl.DSL
import org.jooq.impl.SQLDataType
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Hand-maintained jOOQ table handles for auth tables (avoids waiting on codegen).
 */
object AppUserTable {
    val TABLE = DSL.table(DSL.name("app_user"))
    val ID = DSL.field(DSL.name("app_user", "id"), SQLDataType.UUID.nullable(false))
    val EMAIL = DSL.field(DSL.name("app_user", "email"), SQLDataType.CLOB.nullable(false))
    val DISPLAY_NAME = DSL.field(DSL.name("app_user", "display_name"), SQLDataType.CLOB.nullable(false))
    val ROLE = DSL.field(DSL.name("app_user", "role"), SQLDataType.CLOB.nullable(false))
    val CREATED_AT = DSL.field(DSL.name("app_user", "created_at"), SQLDataType.TIMESTAMPWITHTIMEZONE.nullable(false))
}

object InviteTable {
    val TABLE = DSL.table(DSL.name("invite"))
    val ID = DSL.field(DSL.name("invite", "id"), SQLDataType.UUID.nullable(false))
    val TOKEN_HASH = DSL.field(DSL.name("invite", "token_hash"), SQLDataType.CLOB.nullable(false))
    val EMAIL = DSL.field(DSL.name("invite", "email"), SQLDataType.CLOB.nullable(false))
    val DISPLAY_NAME = DSL.field(DSL.name("invite", "display_name"), SQLDataType.CLOB.nullable(false))
    val ROLE = DSL.field(DSL.name("invite", "role"), SQLDataType.CLOB.nullable(false))
    val STATUS = DSL.field(DSL.name("invite", "status"), SQLDataType.CLOB.nullable(false))
    val CREATED_BY_USER_ID = DSL.field(DSL.name("invite", "created_by_user_id"), SQLDataType.UUID)
    val REDEEMED_BY_USER_ID = DSL.field(DSL.name("invite", "redeemed_by_user_id"), SQLDataType.UUID)
    val EXPIRES_AT = DSL.field(DSL.name("invite", "expires_at"), SQLDataType.TIMESTAMPWITHTIMEZONE)
    val CREATED_AT = DSL.field(DSL.name("invite", "created_at"), SQLDataType.TIMESTAMPWITHTIMEZONE.nullable(false))
    val REDEEMED_AT = DSL.field(DSL.name("invite", "redeemed_at"), SQLDataType.TIMESTAMPWITHTIMEZONE)
}

object InviteRequestTable {
    val TABLE = DSL.table(DSL.name("invite_request"))
    val ID = DSL.field(DSL.name("invite_request", "id"), SQLDataType.UUID.nullable(false))
    val EMAIL = DSL.field(DSL.name("invite_request", "email"), SQLDataType.CLOB.nullable(false))
    val DISPLAY_NAME =
        DSL.field(DSL.name("invite_request", "display_name"), SQLDataType.CLOB.nullable(false))
    val STATUS = DSL.field(DSL.name("invite_request", "status"), SQLDataType.CLOB.nullable(false))
    val CREATED_AT =
        DSL.field(DSL.name("invite_request", "created_at"), SQLDataType.TIMESTAMPWITHTIMEZONE.nullable(false))
    val RESOLVED_AT =
        DSL.field(DSL.name("invite_request", "resolved_at"), SQLDataType.TIMESTAMPWITHTIMEZONE)
    val RESOLVED_BY_USER_ID =
        DSL.field(DSL.name("invite_request", "resolved_by_user_id"), SQLDataType.UUID)
    val INVITE_ID = DSL.field(DSL.name("invite_request", "invite_id"), SQLDataType.UUID)
}

object AppSessionTable {
    val TABLE = DSL.table(DSL.name("app_session"))
    val ID = DSL.field(DSL.name("app_session", "id"), SQLDataType.UUID.nullable(false))
    val TOKEN_HASH = DSL.field(DSL.name("app_session", "token_hash"), SQLDataType.CLOB.nullable(false))
    val USER_ID = DSL.field(DSL.name("app_session", "user_id"), SQLDataType.UUID.nullable(false))
    val EXPIRES_AT = DSL.field(DSL.name("app_session", "expires_at"), SQLDataType.TIMESTAMPWITHTIMEZONE.nullable(false))
    val CREATED_AT = DSL.field(DSL.name("app_session", "created_at"), SQLDataType.TIMESTAMPWITHTIMEZONE.nullable(false))
}

/** Extra columns on gedcom_tree from V8 (not yet in generated jOOQ classes). */
object GedcomTreeAuthFields {
    val KIND = DSL.field(DSL.name("gedcom_tree", "kind"), SQLDataType.CLOB.nullable(false))
    val STATUS = DSL.field(DSL.name("gedcom_tree", "status"), SQLDataType.CLOB)
    val CREATED_BY_USER_ID =
        DSL.field(DSL.name("gedcom_tree", "created_by_user_id"), SQLDataType.UUID)
    val CONTRIBUTOR_USER_ID =
        DSL.field(DSL.name("gedcom_tree", "contributor_user_id"), SQLDataType.UUID)
    val INVITE_ID = DSL.field(DSL.name("gedcom_tree", "invite_id"), SQLDataType.UUID)
    val EXPIRES_AT =
        DSL.field(DSL.name("gedcom_tree", "expires_at"), SQLDataType.TIMESTAMPWITHTIMEZONE)
}

data class AppUserRow(
    val id: UUID,
    val email: String,
    val displayName: String,
    val role: String,
    val createdAt: OffsetDateTime,
)

data class InviteRow(
    val id: UUID,
    val tokenHash: String,
    val email: String,
    val displayName: String,
    val role: String,
    val status: String,
    val createdByUserId: UUID?,
    val redeemedByUserId: UUID?,
    val expiresAt: OffsetDateTime?,
    val createdAt: OffsetDateTime,
    val redeemedAt: OffsetDateTime?,
)

data class InviteRequestRow(
    val id: UUID,
    val email: String,
    val displayName: String,
    val status: String,
    val createdAt: OffsetDateTime,
    val resolvedAt: OffsetDateTime?,
    val resolvedByUserId: UUID?,
    val inviteId: UUID?,
)

data class TreeMetaRow(
    val id: UUID,
    val name: String,
    val kind: String,
    val status: String?,
    val createdByUserId: UUID?,
    val contributorUserId: UUID?,
    val inviteId: UUID?,
    val expiresAt: OffsetDateTime?,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
)
