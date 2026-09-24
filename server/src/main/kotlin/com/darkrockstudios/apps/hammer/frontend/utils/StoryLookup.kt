package com.darkrockstudios.apps.hammer.frontend.utils

import com.darkrockstudios.apps.hammer.Account
import com.darkrockstudios.apps.hammer.account.AccountsRepository
import com.darkrockstudios.apps.hammer.project.access.ProjectAccessRepository
import com.darkrockstudios.apps.hammer.project.access.PublicProjectResult
import com.darkrockstudios.apps.hammer.projects.ProjectsRepository
import io.ktor.server.application.ApplicationCall

data class StoryLookup(
	val account: Account,
	val projectName: String,
	val result: PublicProjectResult,
	/** The incoming, already URL-safe route segments, for self-referential links. */
	val penNameSegment: String,
	val projectSegment: String,
)

/**
 * Resolves the `{penName}/{projectName}` route parameters of a public story URL to the access
 * the reader gets with [password]. Null when the author or project does not exist.
 */
suspend fun ApplicationCall.lookUpStory(
	accountsRepository: AccountsRepository,
	projectsRepository: ProjectsRepository,
	projectAccessRepository: ProjectAccessRepository,
	password: String?,
): StoryLookup? {
	val penNameParam = parameters["penName"]
	val projectNameParam = parameters["projectName"]
	if (penNameParam.isNullOrBlank() || projectNameParam.isNullOrBlank()) return null

	// Pen name verbatim first, then dashes as spaces.
	val account = resolveByPenName(penNameParam) { accountsRepository.findAccountByPenName(it) } ?: return null
	val penName = account.pen_name ?: return null

	// The id embedded in the segment is authoritative; the slug beside it is decorative.
	val projectName = projectsRepository.findProjectByUrlSegment(account.id, projectNameParam)?.name
		?: return null

	return StoryLookup(
		account = account,
		projectName = projectName,
		result = projectAccessRepository.findAccessibleProject(penName, projectName, password),
		penNameSegment = penNameParam,
		projectSegment = projectNameParam,
	)
}
