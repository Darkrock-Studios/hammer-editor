package com.darkrockstudios.apps.hammer.admin

import com.darkrockstudios.apps.hammer.base.validate.EmailValidator
import com.darkrockstudios.apps.hammer.utilities.Msg
import com.darkrockstudios.apps.hammer.utilities.ServerResult
import kotlin.time.Instant

class AdminComponent(
	private val whiteListRepository: WhiteListRepository,
) {
	suspend fun getWhiteList(): List<String> {
		return whiteListRepository.getWhiteList()
	}

	/** A null [expires] never expires, matching the pre-expiry behaviour. */
	suspend fun addToWhiteList(email: String, expires: Instant? = null): ServerResult<Unit> {
		return when {
			!EmailValidator.validate(email) ->
				ServerResult.failure("Invalid email", Msg.r("api_admin_addtowhitelist_invalidemail"))

			!whiteListRepository.validateExpiry(expires) ->
				ServerResult.failure("Invalid expiry", Msg.r("api_admin_addtowhitelist_invalidexpiry"))

			else -> {
				whiteListRepository.addToWhiteList(email, expires = expires)
				ServerResult.success(Unit)
			}
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