package com.shejera.services

import com.shejera.api.BadRequestException
import com.shejera.api.NotFoundException
import com.shejera.auth.AuthPrincipal
import com.shejera.importing.ImportSessionStore
import com.shejera.models.AddChildRequest
import com.shejera.models.CreateContributionTreeRequest
import com.shejera.models.CreateFamilyRequest
import com.shejera.models.CreateIndividualRequest
import com.shejera.models.ImportCommitResponse
import com.shejera.models.RecognizedTree
import com.shejera.models.SpouseRequest
import java.util.UUID

class ImportCommitService(
    private val authService: AuthService,
    private val individualService: IndividualService,
    private val familyService: FamilyService,
) {
    fun commit(
        principal: AuthPrincipal,
        treeName: String?,
        expiresInDays: Int?,
        targetTreeId: UUID?,
    ): ImportCommitResponse {
        val session =
            ImportSessionStore.get(principal.id)
                ?: throw NotFoundException("No import session. Upload and scan an image first.")
        val recognized =
            session.recognizedTree
                ?: throw BadRequestException("No scan result yet. Run scan first.")

        val treeId =
            when {
                targetTreeId != null -> {
                    authService.requireWrite(principal, targetTreeId).treeId
                }
                else -> {
                    val existing =
                        authService.listTrees(principal).firstOrNull {
                            it.kind == "contribution" && it.status == "draft" && it.canWrite
                        }
                    if (existing != null) {
                        UUID.fromString(existing.id)
                    } else {
                        val name =
                            treeName?.trim()?.takeIf { it.isNotEmpty() }
                                ?: "${principal.user.displayName} Import"
                        val created =
                            authService.createContributionTree(
                                principal,
                                CreateContributionTreeRequest(
                                    name = name,
                                    expiresInDays = expiresInDays ?: 30,
                                ),
                            )
                        UUID.fromString(created.id)
                    }
                }
            }

        authService.requireWrite(principal, treeId)
        // Replace any existing draft content (avoid append duplicates on re-commit).
        familyService.clearTree(treeId)
        individualService.clearTree(treeId)
        persistRecognizedTree(recognized, treeId)
        return ImportCommitResponse(
            treeId = treeId.toString(),
            personCount = recognized.people.size,
            familyCount = recognized.families.size,
        )
    }

    private fun persistRecognizedTree(
        tree: RecognizedTree,
        treeId: UUID,
    ) {
        val tempToId = linkedMapOf<String, String>()
        for (person in tree.people) {
            val sex =
                when (person.sex?.uppercase()) {
                    "M", "F", "X", "U" -> person.sex!!.uppercase()
                    else -> null
                }
            val given = person.givenName?.trim().orEmpty()
            val surname = person.surname?.trim().orEmpty()
            val created =
                individualService.create(
                    CreateIndividualRequest(
                        givenName = given.ifBlank { person.tempId },
                        surname = surname,
                        sex = sex,
                        isLiving = person.deathDate.isNullOrBlank(),
                        birthDate = person.birthDate?.trim()?.takeIf { it.isNotEmpty() },
                        deathDate = person.deathDate?.trim()?.takeIf { it.isNotEmpty() },
                    ),
                    treeId,
                )
            tempToId[person.tempId] = created.id
        }

        for (family in tree.families) {
            val spouses =
                family.spouseTempIds.mapNotNull { tempId ->
                    val individualId = tempToId[tempId] ?: return@mapNotNull null
                    val person = tree.people.find { it.tempId == tempId }
                    val role =
                        when (person?.sex?.uppercase()) {
                            "F" -> "WIFE"
                            else -> "HUSB"
                        }
                    SpouseRequest(individualId = individualId, role = role)
                }
            if (spouses.isEmpty()) continue

            val createdFamily =
                familyService.create(
                    CreateFamilyRequest(spouses = spouses.take(2)),
                    treeId,
                )
            for (childTempId in family.childTempIds) {
                val childId = tempToId[childTempId] ?: continue
                familyService.addChild(
                    familyId = UUID.fromString(createdFamily.id),
                    request = AddChildRequest(individualId = childId),
                    treeId = treeId,
                )
            }
        }
    }
}
