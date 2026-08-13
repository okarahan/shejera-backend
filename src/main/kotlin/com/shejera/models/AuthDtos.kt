package com.shejera.models

import kotlinx.serialization.Serializable

@Serializable
data class MeResponse(
    val id: String,
    val email: String,
    val displayName: String,
    val role: String,
    val canManageInvites: Boolean,
    val canWriteMainTree: Boolean,
    val contributionTreeId: String? = null,
    val contributionTreeStatus: String? = null,
)

@Serializable
data class InvitePreviewResponse(
    val email: String,
    val displayName: String,
    val role: String,
    val status: String,
    val expired: Boolean,
)

@Serializable
data class RedeemInviteRequest(
    val token: String,
)

@Serializable
data class CreateInviteRequest(
    val email: String,
    val displayName: String,
    val role: String = "contributor",
    val expiresInDays: Int? = 30,
)

@Serializable
data class RequestInviteRequest(
    val email: String,
    val displayName: String? = null,
)

@Serializable
data class RequestInviteResponse(
    val ok: Boolean = true,
    val message: String,
    val emailSent: Boolean = false,
)

@Serializable
data class InviteRequestResponse(
    val id: String,
    val email: String,
    val displayName: String,
    val status: String,
    val createdAt: String,
    val resolvedAt: String? = null,
    val inviteId: String? = null,
)

@Serializable
data class ApproveInviteRequestBody(
    val expiresInDays: Int? = 30,
)

@Serializable
data class ApproveInviteRequestResponse(
    val request: InviteRequestResponse,
    val invite: InviteResponse,
    val emailSent: Boolean,
)

@Serializable
data class InviteResponse(
    val id: String,
    val email: String,
    val displayName: String,
    val role: String,
    val status: String,
    val token: String? = null,
    val invitePath: String? = null,
    /** Absolute invite URL when SHEJERA_INVITE_ORIGIN is set (e.g. http://shejera…/contrib/...). */
    val inviteUrl: String? = null,
    val expiresAt: String? = null,
    val createdAt: String,
    val redeemedAt: String? = null,
    /** Short reference for admin list (not the secret invite token). */
    val code: String? = null,
    /** draft | submitted | merged | null if no contribution tree yet. */
    val contributionTreeStatus: String? = null,
)

@Serializable
data class TreeResponse(
    val id: String,
    val name: String,
    val kind: String,
    val status: String? = null,
    val expiresAt: String? = null,
    val canWrite: Boolean,
    val contributorUserId: String? = null,
    val createdAt: String,
)

@Serializable
data class CreateContributionTreeRequest(
    val name: String,
    val expiresInDays: Int? = 30,
)

@Serializable
data class ImportCommitResponse(
    val treeId: String,
    val personCount: Int,
    val familyCount: Int,
)


@Serializable
data class ImportCommitRequest(
    val treeName: String? = null,
    val expiresInDays: Int? = 30,
    val treeId: String? = null,
)
