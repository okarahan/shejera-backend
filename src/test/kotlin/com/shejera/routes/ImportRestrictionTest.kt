package com.shejera.routes

import com.shejera.db.AppUserRow
import com.shejera.importing.StubImageRecognizer
import com.shejera.models.ImportCommitRequest
import com.shejera.plugins.ErrorResponse
import com.shejera.routes.AuthServiceKey
import com.shejera.plugins.DslContextKey
import com.shejera.services.AuthService
import com.shejera.services.ImportCommitService
import com.shejera.services.ImportService
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import java.time.OffsetDateTime
import java.util.UUID
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Integration tests for the import endpoint role restriction.
 *
 * The tests use an embedded, in-memory SQLite database via jOOQ so no
 * external Postgres is required. Authentication is exercised through the
 * real AuthService JWT verification using the dev secret.
 */
class ImportRestrictionTest {

    private fun createTestAuthService(dsl: DSLContext): AuthService =
        AuthService(
            dsl = dsl,
            emailService = mockEmailService(),
            treeRepository = com.shejera.repositories.TreeRepository(dsl),
        )

    private fun mockEmailService() =
        com.shejera.services.EmailService(com.shejera.config.SmtpConfig.empty())

    private fun setupTestDb(): DSLContext {
        val ds = org.sqlite.SQLiteDataSource().apply {
            url = "jdbc:sqlite::memory:"
        }
        val dsl = DSL.using(ds, SQLDialect.SQLITE)
        dsl.execute(
            """
            CREATE TABLE app_user (
                id UUID PRIMARY KEY,
                email TEXT NOT NULL UNIQUE,
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
        email: String,
        role: String,
    ): AppUserRow {
        val now = OffsetDateTime.now()
        dsl.execute(
            """
            INSERT INTO app_user (id, email, display_name, role, created_at)
            VALUES (?, ?, ?, ?, ?)
            """.trimIndent(),
            id, email, "Test $role", role, now.toString()
        )
        return AppUserRow(id, email, "Test $role", role, now)
    }

    private fun createJwt(secret: String, userId: UUID, role: String): String {
        val header = base64Url("""{"alg":"HS256","typ":"JWT"}""")
        val exp = (System.currentTimeMillis() / 1000) + 3600
        val payload = base64Url(
            """{"sub":"$userId","role":"$role","exp":$exp}"""
        )
        val signingInput = "$header.$payload"
        val signature = javax.crypto.Mac.getInstance("HmacSHA256").run {
            init(javax.crypto.spec.SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
            doFinal(signingInput.toByteArray())
        }
        return "$signingInput.${base64UrlBytes(signature)}"
    }

    private fun base64Url(input: String): String =
        java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString(input.toByteArray())

    private fun base64UrlBytes(bytes: ByteArray): String =
        java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private fun testShejeraApp(
        dsl: DSLContext,
        authService: AuthService,
        testBlock: suspend io.ktor.server.testing.ApplicationTestBuilder.() -> Unit,
    ) = testApplication {
        application {
            val treeRepository = com.shejera.repositories.TreeRepository(dsl)
            val individualRepository = com.shejera.repositories.IndividualRepository(dsl)
            val familyRepository = com.shejera.repositories.FamilyRepository(dsl)
            val eventRepository = com.shejera.repositories.EventRepository(dsl)
            val placeRepository = com.shejera.repositories.PlaceRepository(dsl)
            val importService = ImportService(StubImageRecognizer(), "stub")
            val importCommitService = ImportCommitService(
                authService = authService,
                individualService = com.shejera.services.IndividualService(
                    dsl = dsl,
                    treeRepository = treeRepository,
                    individualRepository = individualRepository,
                    familyRepository = familyRepository,
                    eventRepository = eventRepository,
                    placeRepository = placeRepository,
                ),
                familyService = com.shejera.services.FamilyService(
                    dsl = dsl,
                    treeRepository = treeRepository,
                    individualRepository = individualRepository,
                    familyRepository = familyRepository,
                    placeRepository = placeRepository,
                ),
            )
            // The routes and services below only exercise upload/scan/commit;
            // we keep DB dependencies present to mirror production wiring.
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; encodeDefaults = true })
            }
            install(StatusPages) {
                exception<com.shejera.api.ApiException> { call, cause ->
                    call.respond(
                        io.ktor.http.HttpStatusCode.fromValue(cause.statusCode),
                        ErrorResponse(error = cause.message ?: "Request failed")
                    )
                }
            }
            attributes.put(AuthServiceKey, authService)
            attributes.put(DslContextKey, dsl)
            attributes.put(
                com.shejera.plugins.DataSourceKey,
                dsl.configuration().connectionProvider().acquire() as DataSource
            )
            routing {
                importRoutes(importService, importCommitService)
            }
        }
        testBlock()
    }

    @Test
    fun `contributor can POST imports upload and receives 201`() = unitTest {
        val dsl = setupTestDb()
        val contributorId = UUID.randomUUID()
        insertUser(dsl, contributorId, "contributor@example.com", "contributor")
        val authService = createTestAuthService(dsl)
        val token = createJwt("dev-jwt-secret", contributorId, "contributor")

        testShejeraApp(dsl, authService) {
            val response = client.post("/imports/upload") {
                header(HttpHeaders.Authorization, "Bearer $token")
                setBody(
                    MultiPartFormDataContent(
                        formData {
                            append(
                                key = "file",
                                value = byteArrayOf(
                                    0x89.toByte(), 0x50, 0x4E, 0x47,
                                    0x0D, 0x0A, 0x1A, 0x0A
                                ),
                                headers = Headers.build {
                                    append(HttpHeaders.ContentDisposition, "filename=\"test.png\"")
                                    append(HttpHeaders.ContentType, ContentType.Image.PNG.toString())
                                },
                            )
                        }
                    )
                )
            }
            assertEquals(HttpStatusCode.Created, response.status)
            assertTrue(response.bodyAsText().contains(""""originalFileName":"test.png""""))
        }
    }

    @Test
    fun `admin receives 403 on POST imports upload`() = unitTest {
        val dsl = setupTestDb()
        val adminId = UUID.randomUUID()
        insertUser(dsl, adminId, "admin@example.com", "admin")
        val authService = createTestAuthService(dsl)
        val token = createJwt("dev-jwt-secret", adminId, "admin")

        testShejeraApp(dsl, authService) {
            val response = client.post("/imports/upload") {
                header(HttpHeaders.Authorization, "Bearer $token")
                setBody(
                    MultiPartFormDataContent(
                        formData {
                            append(
                                key = "file",
                                value = byteArrayOf(
                                    0x89.toByte(), 0x50, 0x4E, 0x47,
                                    0x0D, 0x0A, 0x1A, 0x0A
                                ),
                                headers = Headers.build {
                                    append(HttpHeaders.ContentDisposition, "filename=\"test.png\"")
                                    append(HttpHeaders.ContentType, ContentType.Image.PNG.toString())
                                },
                            )
                        }
                    )
                )
            }
            assertEquals(HttpStatusCode.Forbidden, response.status)
            assertTrue(response.bodyAsText().contains("Only contributors may import"))
        }
    }

    @Test
    fun `admin receives 403 on POST imports scan`() = unitTest {
        val dsl = setupTestDb()
        val adminId = UUID.randomUUID()
        insertUser(dsl, adminId, "admin@example.com", "admin")
        val authService = createTestAuthService(dsl)
        val token = createJwt("dev-jwt-secret", adminId, "admin")

        testShejeraApp(dsl, authService) {
            val response = client.post("/imports/scan") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            assertEquals(HttpStatusCode.Forbidden, response.status)
            assertTrue(response.bodyAsText().contains("Only contributors may import"))
        }
    }

    @Test
    fun `admin receives 403 on GET imports preview`() = unitTest {
        val dsl = setupTestDb()
        val adminId = UUID.randomUUID()
        insertUser(dsl, adminId, "admin@example.com", "admin")
        val authService = createTestAuthService(dsl)
        val token = createJwt("dev-jwt-secret", adminId, "admin")

        testShejeraApp(dsl, authService) {
            val response = client.get("/imports/preview") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            assertEquals(HttpStatusCode.Forbidden, response.status)
            assertTrue(response.bodyAsText().contains("Only contributors may import"))
        }
    }

    @Test
    fun `admin receives 403 on POST imports commit`() = unitTest {
        val dsl = setupTestDb()
        val adminId = UUID.randomUUID()
        insertUser(dsl, adminId, "admin@example.com", "admin")
        val authService = createTestAuthService(dsl)
        val token = createJwt("dev-jwt-secret", adminId, "admin")

        testShejeraApp(dsl, authService) {
            val response = client.post("/imports/commit") {
                header(HttpHeaders.Authorization, "Bearer $token")
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody(Json.encodeToString(ImportCommitRequest.serializer(), ImportCommitRequest()))
            }
            assertEquals(HttpStatusCode.Forbidden, response.status)
            assertTrue(response.bodyAsText().contains("Only contributors may import"))
        }
    }

    @Test
    fun `admin can GET imports status (not restricted) returns 200`() = unitTest {
        val dsl = setupTestDb()
        val adminId = UUID.randomUUID()
        insertUser(dsl, adminId, "admin@example.com", "admin")
        val authService = createTestAuthService(dsl)
        val token = createJwt("dev-jwt-secret", adminId, "admin")

        testShejeraApp(dsl, authService) {
            val response = client.get("/imports/status") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            assertEquals(HttpStatusCode.OK, response.status)
        }
    }

    @Test
    fun `unauthenticated request to imports upload receives 401`() = unitTest {
        val dsl = setupTestDb()
        val authService = createTestAuthService(dsl)

        testShejeraApp(dsl, authService) {
            val response = client.post("/imports/upload") {
                setBody(
                    MultiPartFormDataContent(
                        formData {
                            append(
                                key = "file",
                                value = byteArrayOf(
                                    0x89.toByte(), 0x50, 0x4E, 0x47,
                                    0x0D, 0x0A, 0x1A, 0x0A
                                ),
                                headers = Headers.build {
                                    append(HttpHeaders.ContentDisposition, "filename=\"test.png\"")
                                    append(HttpHeaders.ContentType, ContentType.Image.PNG.toString())
                                },
                            )
                        }
                    )
                )
            }
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }
    }

    private fun unitTest(block: suspend () -> Unit) =
        kotlinx.coroutines.test.runTest { block() }
}
