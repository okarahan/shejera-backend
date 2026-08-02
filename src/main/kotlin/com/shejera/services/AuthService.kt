package com.shejera.services

import com.shejera.api.BadRequestException
import com.shejera.api.ConflictException
import com.shejera.api.ForbiddenException
import com.shejera.api.NotFoundException
import com.shejera.api.UnauthorizedException
import com.shejera.auth.AuthPrincipal
import com.shejera.auth.AuthTokens
import com.shejera.auth.TreeAccess
import com.shejera.db.InviteRow
import com.shejera.db.TreeMetaRow
import com.shejera.models.CreateContributionTreeRequest
import com.shejera.models.CreateInviteRequest
import com.shejera.models.InvitePreviewResponse
import com.shejera.models.InviteResponse
import com.shejera.models.MeResponse
import com.shejera.models.TreeResponse
import com.shejera.repositories.InviteRepository
import com.shejera.repositories.TreeRepository
import com.shejera.repositories.UserRepository
import org.jooq.DSLContext
import org.jooq.exception.DataAccessException
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.time.OffsetDateTime
import java.util.UUID

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class AuthService(
    private val dsl: DSLContext,
    private val userRepository: UserRepository = UserRepository(dsl),
    private val inviteRepository: InviteRepository = InviteRepository(dsl),
    private val treeRepository: TreeRepository = TreeRepository(dsl),
) {
    private val jwtSecret: String =
        System.getenv("SHEJERA_JWT_SECRET")?.takeIf { it.isNotBlank() } ?: "dev-jwt-secret"
    private val jwtTtlDays: Long =
        System.getenv("SHEJERA_JWT_TTL_DAYS")?.toLongOrNull()?.coerceAtLeast(1) ?: 30

    @Serializable
    private data class JwtClaims(
        val sub: String,
        val role: String,
        val email: String? = null,
        val exp: Long,
    )

    private val json = Json { ignoreUnknownKeys = true }

    private fun base64UrlEncode(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private fun base64UrlDecode(input: String): ByteArray {
        val padding = (4 - (input.length % 4)) % 4
        val padded = input + "=".repeat(padding)
        return Base64.getUrlDecoder().decode(padded)
    }

    private fun createJwt(
        userId: UUID,
        role: String,
        email: String?,
        expiresAt: OffsetDateTime,
    ): String {
        val headerJson = """{"alg":"HS256","typ":"JWT"}"""
        val payloadJson =
            json.encodeToString(
                JwtClaims(
                    sub = userId.toString(),
                    role = role,
                    email = email,
                    exp = expiresAt.toEpochSecond(),
                ),
            )
        val headerB64 = base64UrlEncode(headerJson.toByteArray(StandardCharsets.UTF_8))
        val payloadB64 = base64UrlEncode(payloadJson.toByteArray(StandardCharsets.UTF_8))
        val signingInput = "$headerB64.$payloadB64"

        val mac =
            Mac.getInstance("HmacSHA256").apply {
                init(SecretKeySpec(jwtSecret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
            }
        val signature = mac.doFinal(signingInput.toByteArray(StandardCharsets.UTF_8))
        val signatureB64 = base64UrlEncode(signature)
        return "$signingInput.$signatureB64"
    }

    private fun verifyJwt(rawToken: String): JwtClaims? {
        val parts = rawToken.split('.')
        if (parts.size != 3) return null

        val (headerB64, payloadB64, signatureB64) = parts
        val signingInput = "$headerB64.$payloadB64"

        val mac =
            Mac.getInstance("HmacSHA256").apply {
                init(SecretKeySpec(jwtSecret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
            }
        val expectedSignature = mac.doFinal(signingInput.toByteArray(StandardCharsets.UTF_8))
        val actualSignature = base64UrlDecode(signatureB64)
        if (!java.security.MessageDigest.isEqual(expectedSignature, actualSignature)) return null

        val payloadJson = String(base64UrlDecode(payloadB64), StandardCharsets.UTF_8)
        val claims = try { json.decodeFromString<JwtClaims>(payloadJson) } catch (_: Exception) { return null }
        if (claims.exp <= OffsetDateTime.now().toEpochSecond()) return null

        return claims
    }

    fun resolvePrincipal(rawToken: String?): AuthPrincipal? {
        if (rawToken.isNullOrBlank()) return null
        val claims = verifyJwt(rawToken) ?: return null
        val userId = try { UUID.fromString(claims.sub) } catch (_: Exception) { return null }
        val user = userRepository.findById(userId) ?: return null
        return AuthPrincipal(user)
    }

    fun requirePrincipal(rawToken: String?): AuthPrincipal =
        resolvePrincipal(rawToken) ?: throw UnauthorizedException()

    fun previewInvite(rawToken: String): InvitePreviewResponse {
        val invite =
            inviteRepository.findByTokenHash(AuthTokens.sha256(rawToken))
                ?: throw NotFoundException("Invite not found")
        return InvitePreviewResponse(
            email = invite.email,
            displayName = invite.displayName,
            role = invite.role,
            status = invite.status,
            expired = isInviteExpired(invite),
        )
    }

    data class RedeemResult(
        val principal: AuthPrincipal,
        val sessionToken: String,
        val expiresAt: OffsetDateTime,
    )

    fun redeemInvite(rawToken: String): RedeemResult {
        val tokenHash = AuthTokens.sha256(rawToken)
        val invite =
            inviteRepository.findByTokenHash(tokenHash)
                ?: throw NotFoundException("Invite not found")

        if (invite.status == "revoked") {
            throw ForbiddenException("Invite has been revoked")
        }
        if (isInviteExpired(invite)) {
            throw ForbiddenException("Invite has expired")
        }

        val user =
            dsl.transactionResult { ctx ->
                val tx = ctx.dsl()
                val users = UserRepository(tx)
                val invites = InviteRepository(tx)

                when (invite.status) {
                    "pending" -> {
                        if (users.findByEmail(invite.email) != null) {
                            throw ConflictException("A user with this email already exists")
                        }
                        val created =
                            users.insert(
                                email = invite.email,
                                displayName = invite.displayName,
                                role = invite.role,
                            )
                        invites.markRedeemed(invite.id, created.id)
                        created
                    }
                    "redeemed" -> {
                        invite.redeemedByUserId?.let { users.findById(it) }
                            ?: users.findByEmail(invite.email)
                            ?: throw BadRequestException("Invite was redeemed but user is missing")
                    }
                    else -> throw ForbiddenException("Invite cannot be used")
                }
            }

        val expiresAt = OffsetDateTime.now().plusDays(jwtTtlDays)
        val jwtToken =
            createJwt(
                userId = user.id,
                role = user.role,
                email = user.email,
                expiresAt = expiresAt,
            )

        return RedeemResult(
            principal = AuthPrincipal(user),
            sessionToken = jwtToken,
            expiresAt = expiresAt,
        )
    }

    fun logout(rawToken: String?) {
        // Stateless JWT: logout only clears the cookie client-side.
    }

    fun me(principal: AuthPrincipal): MeResponse {
        val contribution = treeRepository.findActiveContributionForUser(principal.id)
        return MeResponse(
            id = principal.id.toString(),
            email = principal.user.email,
            displayName = principal.user.displayName,
            role = principal.user.role,
            canManageInvites = principal.isAdmin,
            canWriteMainTree = principal.isAdmin,
            contributionTreeId = contribution?.id?.toString(),
            contributionTreeStatus = contribution?.status,
        )
    }

    fun createInvite(
        principal: AuthPrincipal,
        request: CreateInviteRequest,
    ): InviteResponse {
        if (!principal.isAdmin) throw ForbiddenException()
        val email = request.email.trim()
        val displayName = request.displayName.trim()
        if (email.isEmpty() || !email.contains("@")) {
            throw BadRequestException("Valid email is required")
        }
        if (displayName.isEmpty()) {
            throw BadRequestException("displayName is required")
        }
        val role = request.role.trim().lowercase()
        if (role !in setOf("admin", "contributor")) {
            throw BadRequestException("role must be admin or contributor")
        }
        if (userRepository.findByEmail(email) != null) {
            throw ConflictException("A user with this email already exists")
        }

        val rawToken = AuthTokens.newToken()
        val expiresAt =
            request.expiresInDays?.let {
                if (it < 1) throw BadRequestException("expiresInDays must be >= 1")
                OffsetDateTime.now().plusDays(it.toLong())
            }

        val invite =
            inviteRepository.insert(
                tokenHash = AuthTokens.sha256(rawToken),
                email = email,
                displayName = displayName,
                role = role,
                createdByUserId = principal.id,
                expiresAt = expiresAt,
            )

        return toInviteResponse(invite, rawToken)
    }

    fun listInvites(principal: AuthPrincipal): List<InviteResponse> {
        if (!principal.isAdmin) throw ForbiddenException()
        return inviteRepository.listAll().map { invite ->
            val contributionStatus =
                invite.redeemedByUserId?.let { userId ->
                    treeRepository.findActiveContributionForUser(userId)?.status
                }
            toInviteResponse(invite, token = null, contributionTreeStatus = contributionStatus)
        }
    }

    fun revokeInvite(
        principal: AuthPrincipal,
        inviteId: UUID,
    ) {
        if (!principal.isAdmin) throw ForbiddenException()
        if (!inviteRepository.revoke(inviteId)) {
            throw NotFoundException("Pending invite not found: $inviteId")
        }
    }

    fun ensureBootstrapAdminInvite(): String? {
        if (userRepository.countAdmins() > 0) return null

        val fixedToken = System.getenv("SHEJERA_BOOTSTRAP_TOKEN")?.takeIf { it.isNotBlank() }
        val email =
            System.getenv("SHEJERA_BOOTSTRAP_EMAIL")?.takeIf { it.isNotBlank() }
                ?: "admin@shejera.local"
        val displayName =
            System.getenv("SHEJERA_BOOTSTRAP_NAME")?.takeIf { it.isNotBlank() }
                ?: "Admin"

        val pendingAdmins =
            inviteRepository.listAll().filter {
                it.status == "pending" && it.role == "admin" && !isInviteExpired(it)
            }

        // Fixed token in secrets: replace any unknown pending admin invite so the link is recoverable.
        if (fixedToken != null) {
            val wantHash = AuthTokens.sha256(fixedToken)
            if (pendingAdmins.any { it.tokenHash == wantHash }) return null
            pendingAdmins.forEach { inviteRepository.revoke(it.id) }
            inviteRepository.insert(
                tokenHash = wantHash,
                email = email,
                displayName = displayName,
                role = "admin",
                createdByUserId = null,
                expiresAt = OffsetDateTime.now().plusDays(30),
            )
            return fixedToken
        }

        if (pendingAdmins.isNotEmpty()) return null

        val rawToken = AuthTokens.newToken()
        inviteRepository.insert(
            tokenHash = AuthTokens.sha256(rawToken),
            email = email,
            displayName = displayName,
            role = "admin",
            createdByUserId = null,
            expiresAt = OffsetDateTime.now().plusDays(30),
        )
        return rawToken
    }

    fun treeAccess(
        principal: AuthPrincipal,
        treeId: UUID,
    ): TreeAccess {
        val tree =
            treeRepository.findById(treeId)
                ?: throw NotFoundException("Tree not found: $treeId")
        val access = TreeAccess(principal, tree)
        if (!access.canRead()) throw ForbiddenException("No access to this tree")
        return access
    }

    fun requireWrite(
        principal: AuthPrincipal,
        treeId: UUID,
    ): TreeAccess {
        val access = treeAccess(principal, treeId)
        if (!access.canWrite()) {
            throw ForbiddenException("Write access denied for this tree")
        }
        return access
    }

    fun resolveTreeId(
        principal: AuthPrincipal,
        requestedTreeId: UUID?,
    ): UUID {
        if (requestedTreeId != null) {
            treeAccess(principal, requestedTreeId)
            return requestedTreeId
        }
        return treeRepository.getMainTreeId()
    }

    fun listTrees(principal: AuthPrincipal): List<TreeResponse> =
        treeRepository.listAccessible(principal.id, principal.isAdmin).map { tree ->
            toTreeResponse(principal, tree)
        }

    fun createContributionTree(
        principal: AuthPrincipal,
        request: CreateContributionTreeRequest,
    ): TreeResponse {
        val name = request.name.trim()
        if (name.isEmpty()) throw BadRequestException("name is required")

        val expiresInDays = request.expiresInDays ?: 30
        if (expiresInDays < 1) throw BadRequestException("expiresInDays must be >= 1")
        val expiresAt = OffsetDateTime.now().plusDays(expiresInDays.toLong())

        // Contributors: one active tree (unique index on contributor_user_id).
        // Admins: many trees (contributor_user_id left null; created_by tracks owner).
        val contributorUserId: UUID? =
            if (principal.isAdmin) {
                null
            } else {
                if (treeRepository.findActiveContributionForUser(principal.id) != null) {
                    throw ConflictException(
                        "Contributor already has a contribution tree. Ask an admin for another invite.",
                    )
                }
                principal.id
            }

        val tree =
            try {
                treeRepository.createContribution(
                    name = name,
                    createdByUserId = principal.id,
                    contributorUserId = contributorUserId,
                    inviteId = null,
                    expiresAt = expiresAt,
                )
            } catch (_: DataAccessException) {
                throw ConflictException(
                    "Could not create contribution tree (limit one active tree per contributor)",
                )
            }

        return toTreeResponse(principal, tree)
    }

    fun submitContribution(
        principal: AuthPrincipal,
        treeId: UUID,
    ): TreeResponse {
        val access = treeAccess(principal, treeId)
        if (access.meta.kind != "contribution") {
            throw BadRequestException("Only contribution trees can be submitted")
        }
        if (access.meta.status != "draft") {
            throw ConflictException("Tree is not in draft status")
        }
        val isOwnerContributor = access.meta.contributorUserId == principal.id
        val isCreatorAdmin = principal.isAdmin && access.meta.createdByUserId == principal.id
        if (!principal.isAdmin && !isOwnerContributor) {
            throw ForbiddenException()
        }
        if (!isOwnerContributor && !isCreatorAdmin && !principal.isAdmin) {
            throw ForbiddenException()
        }
        treeRepository.updateStatus(treeId, "submitted")
        return toTreeResponse(principal, treeRepository.findById(treeId)!!)
    }

    fun discardContribution(
        principal: AuthPrincipal,
        treeId: UUID,
    ) {
        val tree =
            treeRepository.findById(treeId)
                ?: throw NotFoundException("Tree not found: $treeId")
        if (tree.kind != "contribution") {
            throw BadRequestException("Only contribution trees can be discarded")
        }
        if (tree.status != "draft") {
            throw ConflictException("Only draft contribution trees can be discarded")
        }
        val isOwner =
            tree.contributorUserId != null && tree.contributorUserId == principal.id
        val isCreatorAdmin =
            principal.isAdmin && tree.createdByUserId == principal.id
        if (!principal.isAdmin && !isOwner && !isCreatorAdmin) {
            throw ForbiddenException()
        }
        if (!treeRepository.delete(treeId)) {
            throw NotFoundException("Tree not found: $treeId")
        }
    }

    private fun isInviteExpired(invite: InviteRow): Boolean {
        val expires = invite.expiresAt ?: return false
        return expires.isBefore(OffsetDateTime.now())
    }

    private fun toInviteResponse(
        invite: InviteRow,
        token: String?,
        contributionTreeStatus: String? = null,
    ): InviteResponse {
        val path = token?.let { "/contrib/$it" }
        val origin =
            System.getenv("SHEJERA_INVITE_ORIGIN")
                ?.trim()
                ?.trimEnd('/')
                ?.takeIf { it.isNotBlank() }
        return InviteResponse(
            id = invite.id.toString(),
            email = invite.email,
            displayName = invite.displayName,
            role = invite.role,
            status = invite.status,
            token = token,
            invitePath = path,
            inviteUrl = if (path != null && origin != null) "$origin$path" else null,
            expiresAt = invite.expiresAt?.toString(),
            createdAt = invite.createdAt.toString(),
            redeemedAt = invite.redeemedAt?.toString(),
            code = invite.id.toString().take(8),
            contributionTreeStatus = contributionTreeStatus,
        )
    }

    private fun toTreeResponse(
        principal: AuthPrincipal,
        tree: TreeMetaRow,
    ): TreeResponse {
        val access = TreeAccess(principal, tree)
        return TreeResponse(
            id = tree.id.toString(),
            name = tree.name,
            kind = tree.kind,
            status = tree.status,
            expiresAt = tree.expiresAt?.toString(),
            canWrite = access.canWrite(),
            contributorUserId = tree.contributorUserId?.toString(),
            createdAt = tree.createdAt.toString(),
        )
    }
}
