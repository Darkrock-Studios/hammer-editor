package com.darkrockstudios.apps.hammer.account

class BioService(
	private val accountsRepository: AccountsRepository
) {
	suspend fun setBio(userId: Long, bio: String?): BioResult {
		val account = accountsRepository.getAccount(userId)

		// Bio requires pen name
		if (account.pen_name == null) {
			return BioResult.NO_PEN_NAME
		}

		// Empty/null bio is valid (clears bio)
		if (bio.isNullOrBlank()) {
			accountsRepository.updateBio(userId, null)
			return BioResult.VALID
		}

		val validationResult = validateBio(bio)
		if (validationResult != BioResult.VALID) {
			return validationResult
		}

		accountsRepository.updateBio(userId, bio.trim())
		return BioResult.VALID
	}

	fun validateBio(bio: String): BioResult {
		val trimmed = bio.trim()
		return when {
			trimmed.length > MAX_BIO_LENGTH -> BioResult.TOO_LONG
			else -> BioResult.VALID
		}
	}

	companion object {
		const val MAX_BIO_LENGTH = 1024
	}

	enum class BioResult {
		VALID,
		TOO_LONG,
		NO_PEN_NAME
	}
}
