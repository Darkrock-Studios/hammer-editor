package com.darkrockstudios.apps.hammer.admin

import com.darkrockstudios.apps.hammer.account.AccountsRepository
import com.darkrockstudios.apps.hammer.utilities.Msg
import com.darkrockstudios.apps.hammer.utilities.ServerResult
import com.github.aymanizz.ktori18n.R

class AdminComponent(
	private val whiteListRepository: WhiteListRepository,
) {
	suspend fun getWhiteList(): List<String> {
		return whiteListRepository.getWhiteList()
	}

	suspend fun addToWhiteList(email: String): ServerResult<Unit> {
		return if (AccountsRepository.validateEmail(email)) {
			whiteListRepository.addToWhiteList(email)
			ServerResult.success(Unit)
		} else {
			ServerResult.failure("Invalid email", Msg.r("api.admin.addtowhitelist.invalidemail"))
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