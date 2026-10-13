package com.darkrockstudios.apps.hammer.account

import com.darkrockstudios.apps.hammer.project.access.ProjectAccessRepository

/**
 * Gives up a user's pen name. Their story access entries go with it, because published and
 * shared stories are addressed by pen name.
 */
class ReleasePenNameUseCase(
	private val accountsRepository: AccountsRepository,
	private val projectAccessRepository: ProjectAccessRepository,
) {
	suspend operator fun invoke(userId: Long) {
		accountsRepository.updatePenName(userId, null)
		projectAccessRepository.deleteAllAccessForUser(userId)
	}
}
