package com.shejera.auth

import com.shejera.db.AppUserRow
import com.shejera.db.TreeMetaRow
import java.util.UUID

data class AuthPrincipal(
    val user: AppUserRow,
) {
    val id: UUID get() = user.id
    val isAdmin: Boolean get() = user.role == "admin"
    val isContributor: Boolean get() = user.role == "contributor"
}

class TreeAccess(
    private val principal: AuthPrincipal,
    private val tree: TreeMetaRow,
) {
    val treeId: UUID get() = tree.id
    val meta: TreeMetaRow get() = tree

    fun canRead(): Boolean {
        if (tree.kind == "main") return true
        if (principal.isAdmin) return true
        return tree.contributorUserId != null && tree.contributorUserId == principal.id
    }

    fun canWrite(): Boolean {
        if (!canRead()) return false
        if (tree.kind == "main") return principal.isAdmin
        if (tree.status != "draft") return false
        if (isExpired()) return false
        if (principal.isAdmin) return true
        return tree.contributorUserId != null && tree.contributorUserId == principal.id
    }

    fun isExpired(): Boolean {
        val expires = tree.expiresAt ?: return false
        return expires.isBefore(java.time.OffsetDateTime.now())
    }
}
