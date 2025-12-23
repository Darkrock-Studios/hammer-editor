package com.darkrockstudios.apps.hammer.account

import com.darkrockstudios.apps.hammer.project.access.ProjectAccessRepository

class PenNameService(
	private val accountsRepository: AccountsRepository,
	private val projectAccessRepository: ProjectAccessRepository
) {
	suspend fun setPenName(userId: Long, penName: String): PenNameResult {
		val validationResult = validatePenName(penName)
		if (validationResult != PenNameResult.VALID) {
			return validationResult
		}

		val trimmedName = penName.trim()
		val isAvailable = accountsRepository.isPenNameAvailable(trimmedName, userId)
		if (!isAvailable) {
			return PenNameResult.NOT_AVAILABLE
		}

		accountsRepository.updatePenName(userId, trimmedName)
		return PenNameResult.VALID
	}

	suspend fun releasePenName(userId: Long) {
		accountsRepository.updatePenName(userId, null)
		projectAccessRepository.deleteAllAccessForUser(userId)
	}

	suspend fun isPenNameAvailable(penName: String, excludeUserId: Long? = null): Boolean {
		return accountsRepository.isPenNameAvailable(penName, excludeUserId)
	}

	fun validatePenName(penName: String): PenNameResult {
		val trimmed = penName.trim()
		return when {
			trimmed.length < MIN_PEN_NAME_LENGTH -> PenNameResult.TOO_SHORT
			trimmed.length > MAX_PEN_NAME_LENGTH -> PenNameResult.TOO_LONG
			!PEN_NAME_PATTERN.matches(trimmed) -> PenNameResult.INVALID_CHARACTERS
			else -> PenNameResult.VALID
		}
	}

	companion object {
		const val MIN_PEN_NAME_LENGTH = 4
		const val MAX_PEN_NAME_LENGTH = 32

		// Allow Unicode letters, spaces, hyphens, and underscores for pen names
		// This supports international characters while still being URL-safe when encoded
		private val PEN_NAME_PATTERN = Regex("^[\\p{L}][\\p{L}\\p{N} _-]*$")
	}

	enum class PenNameResult {
		VALID,
		TOO_SHORT,
		TOO_LONG,
		INVALID_CHARACTERS,
		NOT_AVAILABLE
	}
}
