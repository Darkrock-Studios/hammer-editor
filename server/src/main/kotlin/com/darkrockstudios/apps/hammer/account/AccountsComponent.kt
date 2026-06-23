package com.darkrockstudios.apps.hammer.account

import com.darkrockstudios.apps.hammer.Account
import com.darkrockstudios.apps.hammer.ServerConfig
import com.darkrockstudios.apps.hammer.admin.AdminServerConfig
import com.darkrockstudios.apps.hammer.admin.ConfigRepository
import com.darkrockstudios.apps.hammer.admin.WhiteListRepository
import com.darkrockstudios.apps.hammer.base.http.Token
import com.darkrockstudios.apps.hammer.patreon.PatreonConfig
import com.darkrockstudios.apps.hammer.projects.ProjectsRepository
import com.darkrockstudios.apps.hammer.utilities.Msg
import com.darkrockstudios.apps.hammer.utilities.SResult
import com.darkrockstudios.apps.hammer.utilities.ServerResult
import com.darkrockstudios.apps.hammer.utilities.isSuccess

class AccountsComponent(
	private val accountsRepository: AccountsRepository,
	private val whiteListRepository: WhiteListRepository,
	private val projectsRepository: ProjectsRepository,
	private val configRepository: ConfigRepository,
	private val serverConfig: ServerConfig,
) {
	suspend fun createAccount(
		email: String,
		installId: String,
		password: String
	): ServerResult<Token> {
		// If we dont have users, skip whitelist check
		if (accountsRepository.hasUsers() && checkIfWhiteListRejected(email)) {
			return whiteListRejectedFailure()
		}

		val result = accountsRepository.createAccount(email, installId, password)
		if (isSuccess(result)) {
			val token = result.data
			projectsRepository.createUserData(token.userId)
		}

		return result
	}

	suspend fun login(email: String, password: String, installId: String): SResult<Token> {
		if (checkIfWhiteListRejected(email)) {
			return whiteListRejectedFailure()
		}

		return accountsRepository.login(email, password, installId)
	}

	suspend fun refreshToken(
		userId: Long,
		installId: String,
		refreshToken: String
	): SResult<Token> {
		// An unknown userId falls through to refreshToken, which fails the same way
		// a bad token does — never surface AccountNotFound as a 500, since the status
		// difference would reveal whether the account exists.
		val account = accountsRepository.getAccountOrNull(userId)
		if (account != null && checkIfWhiteListRejected(account)) {
			return whiteListRejectedFailure()
		}

		return accountsRepository.refreshToken(userId, installId, refreshToken)
	}

	suspend fun checkIfWhiteListRejected(email: String): Boolean {
		val account = accountsRepository.findAccount(email)
		return if (account != null) {
			checkIfWhiteListRejected(account)
		} else {
			whiteListRepository.useWhiteList() && whiteListRepository.isOnWhiteList(email).not()
		}
	}

	private suspend fun checkIfWhiteListRejected(account: Account): Boolean {
		return !account.is_admin &&
			whiteListRepository.useWhiteList() &&
			whiteListRepository.isOnWhiteList(account.email).not()
	}

	private suspend fun getActivePatreonConfig(): PatreonConfig? {
		if (serverConfig.patreonEnabled != true) return null
		val config = configRepository.get(AdminServerConfig.PATREON_CONFIG)
		return if (config.enabled && config.patreonUrl.isNotBlank()) config else null
	}

	private suspend fun whiteListRejectedFailure(): SResult<Token> {
		val patreonConfig = getActivePatreonConfig()
		return if (patreonConfig != null) {
			val amount = "%.2f".format(patreonConfig.minimumAmountCents / 100.0)
			SResult.failure(
				error = "User not on whitelist - Patreon subscription required",
				displayMessage = Msg.r("login_failure_patreon_notice_message", amount, patreonConfig.patreonUrl)
			)
		} else {
			SResult.failure(
				error = "User not on whitelist",
				displayMessage = Msg.r("api_whitelist_rejected")
			)
		}
	}
}