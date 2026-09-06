package com.shejera.repositories

import com.shejera.db.AppUserTable
import com.shejera.db.GedcomTreeAuthFields
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import java.sql.DriverManager
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for [TreeRepository.listAccessible] contributor-side filtering.
 *
 * Uses an in-memory SQLite database so no external Postgres is required.
 * A single shared connection keeps the in-memory DB alive and consistent
 * across every statement jOOQ issues.
 */
class TreeRepositoryVisibilityTest {

    private fun setupTestDb(): DSLContext {
        // Bind jOOQ to ONE connection: jdbc:sqlite::memory: gives each new
        // connection its own empty database, so a DataSource-backed DSLContext
        // would lose every table between statements.
        val connection = DriverManager.getConnection("jdbc:sqlite::memory:")
        // The generated jOOQ tables are schema-qualified (public.gedcom_tree)
        // from the Postgres codegen; SQLite has no schema, so drop the
        // catalog/schema qualifiers or every query fails with "no such table".
        val dsl = DSL.using(
            connection,
            SQLDialect.SQLITE,
            org.jooq.conf.Settings().withRenderCatalog(false).withRenderSchema(false),
        )
        dsl.execute(
            """
            CREATE TABLE app_user (
                id UUID PRIMARY KEY,
                email TEXT NOT NULL,
                display_name TEXT NOT NULL,
                role TEXT NOT NULL,
                created_at TEXT NOT NULL
            )
            """.trimIndent()
        )
        dsl.execute(
            """
            CREATE TABLE gedcom_tree (
                id UUID PRIMARY KEY,
                name TEXT NOT NULL,
                gedcom_version TEXT NOT NULL DEFAULT '7.0',
                created_at TEXT NOT NULL,
                updated_at TEXT NOT NULL,
                kind TEXT NOT NULL,
                status TEXT,
                created_by_user_id UUID,
                contributor_user_id UUID,
                invite_id UUID,
                expires_at TEXT
            )
            """.trimIndent()
        )
        return dsl
    }

    private fun insertUser(
        dsl: DSLContext,
        id: UUID,
        role: String,
    ) {
        dsl.execute(
            """
            INSERT INTO app_user (id, email, display_name, role, created_at)
            VALUES (?, ?, ?, ?, ?)
            """.trimIndent(),
            id,
            "${UUID.randomUUID()}@example.com",
            "Test $role",
            role,
            OffsetDateTime.now().toString()
        )
    }

    private fun insertTree(
        dsl: DSLContext,
        id: UUID,
        name: String,
        kind: String,
        status: String?,
        contributorUserId: UUID?,
    ) {
        val now = OffsetDateTime.now()
        dsl.execute(
            """
            INSERT INTO gedcom_tree (
                id, name, gedcom_version, created_at, updated_at,
                kind, status, created_by_user_id, contributor_user_id, invite_id, expires_at
            ) VALUES (?, ?, '7.0', ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            id, name, now.toString(), now.toString(),
            kind, status, contributorUserId, contributorUserId, null, null
        )
    }

    @Test
    fun `listAccessible excludes contributions whose contributor role is admin`() {
        val dsl = setupTestDb()
        val repo = TreeRepository(dsl)
        val mainId = UUID.randomUUID()
        val contributorId = UUID.randomUUID()
        val adminId = UUID.randomUUID()

        insertUser(dsl, contributorId, "contributor")
        insertUser(dsl, adminId, "admin")

        insertTree(dsl, mainId, "Main", "main", null, null)
        val contribFromContributor = UUID.randomUUID()
        insertTree(dsl, contribFromContributor, "Contrib", "contribution", "draft", contributorId)
        val contribFromAdmin = UUID.randomUUID()
        insertTree(dsl, contribFromAdmin, "Admin Contrib", "contribution", "draft", adminId)

        val visible = repo.listAccessible(contributorId, isAdmin = false)
        val ids = visible.map { it.id }

        assertTrue(ids.contains(mainId), "main tree should be visible")
        assertTrue(
            ids.contains(contribFromContributor),
            "contributor's own contribution should be visible"
        )
        assertTrue(!ids.contains(contribFromAdmin), "admin contribution must be filtered out")
        assertEquals(2, visible.size)
    }

    @Test
    fun `listAccessible admin caller still does not see admin contributions`() {
        val dsl = setupTestDb()
        val repo = TreeRepository(dsl)
        val mainId = UUID.randomUUID()
        val contributorId = UUID.randomUUID()
        val adminId = UUID.randomUUID()

        insertUser(dsl, contributorId, "contributor")
        insertUser(dsl, adminId, "admin")

        insertTree(dsl, mainId, "Main", "main", null, null)
        val contribFromContributor = UUID.randomUUID()
        insertTree(dsl, contribFromContributor, "Contrib", "contribution", "draft", contributorId)
        val contribFromAdmin = UUID.randomUUID()
        insertTree(dsl, contribFromAdmin, "Admin Contrib", "contribution", "draft", adminId)

        val visible = repo.listAccessible(adminId, isAdmin = true)
        val ids = visible.map { it.id }

        assertTrue(ids.contains(mainId), "main tree should be visible to admin")
        assertTrue(ids.contains(contribFromContributor), "contributor contribution should be visible to admin")
        assertTrue(!ids.contains(contribFromAdmin), "admin contribution must be filtered out for admin caller")
        assertEquals(2, visible.size)
    }
}