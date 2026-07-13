package com.shejera.plugins

import com.shejera.repositories.EventRepository
import com.shejera.repositories.FamilyRepository
import com.shejera.repositories.IndividualRepository
import com.shejera.repositories.PlaceRepository
import com.shejera.repositories.TreeRepository
import com.shejera.routes.familyRoutes
import com.shejera.routes.healthRoutes
import com.shejera.routes.individualRoutes
import com.shejera.routes.openApiRoutes
import com.shejera.services.FamilyService
import com.shejera.services.IndividualService
import io.ktor.server.application.Application
import io.ktor.server.routing.routing

fun Application.configureRouting() {
    val dsl = dsl()

    val treeRepository = TreeRepository(dsl)
    val individualRepository = IndividualRepository(dsl)
    val familyRepository = FamilyRepository(dsl)
    val placeRepository = PlaceRepository(dsl)
    val eventRepository = EventRepository(dsl)

    val individualService =
        IndividualService(
            dsl = dsl,
            treeRepository = treeRepository,
            individualRepository = individualRepository,
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

    routing {
        healthRoutes()
        openApiRoutes()
        individualRoutes(individualService)
        familyRoutes(familyService)
    }
}
