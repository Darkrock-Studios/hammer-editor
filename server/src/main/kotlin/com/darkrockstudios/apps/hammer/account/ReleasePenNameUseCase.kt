package com.darkrockstudios.apps.hammer.account

import com.darkrockstudios.apps.hammer.project.access.ProjectAccessRepository

/** Gives up a user's pen name and removes all of their story access entries. */
class ReleasePenNameUseCase(
	private val accountsRepository: AccountsRepository,
	private val projectAccessRepository: ProjectAccessRepository,
) {
	suspend operator fun invoke(userId: Long) {
		// Access goes first: if clearing the name then fails, the user still has a pen name
		// and can release it again. The other order would strand the access entries.
		projectAccessRepository.deleteAllAccessForUser(userId)
		accountsRepository.updatePenName(userId, null)
	}
}
