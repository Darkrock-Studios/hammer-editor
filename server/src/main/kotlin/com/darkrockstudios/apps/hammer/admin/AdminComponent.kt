package com.darkrockstudios.apps.hammer.admin

import com.darkrockstudios.apps.hammer.account.AccountsRepository

class AdminComponent(
	private val whiteListRepository: WhiteListRepository,
) {
	suspend fun getWhiteList(): List<String> {
		return whiteListRepository.getWhiteList()
	}

	suspend fun addToWhiteList(email: String): Result<Unit> {
		return if (AccountsRepository.validateEmail(email)) {
			whiteListRepository.addToWhiteList(email)
			Result.success(Unit)
		} else {
			Result.failure(IllegalArgumentException("Invalid email"))
		}
	}

	suspend fun removeFromWhiteList(email: String) {
		whiteListRepository.removeFromWhiteList(email)
	}

	suspend fun enableWhiteList() {
		whiteListRepository.setWhiteListEnabled(true)
	}

	suspend fun disableWhiteList() {
		whiteListRepository.setWhiteListEnabled(false)
	}
}