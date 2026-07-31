package com.shejera.plugins

import com.shejera.importing.createImageRecognizerSetup
import com.shejera.repositories.EventRepository
import com.shejera.repositories.FamilyRepository
import com.shejera.repositories.IndividualRepository
import com.shejera.repositories.PlaceRepository
import com.shejera.repositories.TreeRepository
import com.shejera.routes.AuthServiceKey
import com.shejera.routes.authRoutes
import com.shejera.routes.familyRoutes
import com.shejera.routes.healthRoutes
import com.shejera.routes.importRoutes
import com.shejera.routes.individualRoutes
import com.shejera.routes.openApiRoutes
import com.shejera.routes.treeRoutes
import com.shejera.services.AuthService
import com.shejera.services.FamilyService
import com.shejera.services.ImportCommitService
import com.shejera.services.ImportService
import com.shejera.services.IndividualService
import io.ktor.server.application.Application
import io.ktor.server.routing.routing
import org.slf4j.LoggerFactory

fun Application.configureRouting() {
    val dsl = dsl()
    val log = LoggerFactory.getLogger("com.shejera.Bootstrap")

    val treeRepository = TreeRepository(dsl)
    val individualRepository = IndividualRepository(dsl)
    val familyRepository = FamilyRepository(dsl)
    val placeRepository = PlaceRepository(dsl)
    val eventRepository = EventRepository(dsl)

    val authService = AuthService(dsl)
    attributes.put(AuthServiceKey, authService)

    val bootstrapToken = authService.ensureBootstrapAdminInvite()
    if (bootstrapToken != null) {
        log.warn(
            "No admin user yet. Redeem bootstrap invite at /contrib/{} (email from SHEJERA_BOOTSTRAP_EMAIL or admin@shejera.local)",
            bootstrapToken,
        )
    }

    val individualService =
        IndividualService(
            dsl = dsl,
            treeRepository = treeRepository,
            individualRepository = individualRepository,
            familyRepository = familyRepository,
            eventRepository = eventRepository,
            placeRepository = placeRepository,
        )

    val familyService =
        FamilyService(
            dsl = dsl,
            treeRepository = treeRepository,
            individualRepository = individualRepository,
            familyRepository = familyRepository,
            placeRepository = placeRepository,
        )

    val recognizerSetup = createImageRecognizerSetup()
    val importService =
        ImportService(
            imageRecognizer = recognizerSetup.recognizer,
            recognizerMode = recognizerSetup.mode,
        )
    val importCommitService =
        ImportCommitService(
            authService = authService,
            individualService = individualService,
            familyService = familyService,
        )

    routing {
        healthRoutes()
        openApiRoutes()
        authRoutes(authService)
        treeRoutes(authService)
        individualRoutes(individualService, authService)
        familyRoutes(familyService, authService)
        importRoutes(importService, importCommitService)
    }
}
