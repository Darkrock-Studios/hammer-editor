package com.darkrockstudios.apps.hammer.account

import com.darkrockstudios.apps.hammer.Account
import com.darkrockstudios.apps.hammer.admin.WhiteListRepository
import com.darkrockstudios.apps.hammer.base.http.Token
import com.darkrockstudios.apps.hammer.projects.ProjectsRepository
import com.darkrockstudios.apps.hammer.utilities.Msg
import com.darkrockstudios.apps.hammer.utilities.SResult
import com.darkrockstudios.apps.hammer.utilities.ServerResult
import com.darkrockstudios.apps.hammer.utilities.isSuccess
import com.github.aymanizz.ktori18n.R

class AccountsComponent(
	private val accountsRepository: AccountsRepository,
	private val whiteListRepository: WhiteListRepository,
	private val projectsRepository: ProjectsRepository,
) {
	suspend fun createAccount(email: String, installId: String, password: String): ServerResult<Token> {
		// If we dont have users, skip whitelist check
		if (accountsRepository.hasUsers() && whiteListRejected(email)) return ServerResult.failure(
			"not on whitelist",
			Msg.r("api.accounts.create.error.notonwhitelist")
		)

		val result = accountsRepository.createAccount(email, installId, password)
		if (isSuccess(result)) {
			val token = result.data
			projectsRepository.createUserData(token.userId)
		}

		return result
	}

	suspend fun login(email: String, password: String, installId: String): SResult<Token> {
		if (whiteListRejected(email)) return WhiteListRejected()

		return accountsRepository.login(email, password, installId)
	}

	suspend fun refreshToken(userId: Long, installId: String, refreshToken: String): SResult<Token> {
		if (whiteListRejected(userId)) return WhiteListRejected()

		return accountsRepository.refreshToken(userId, installId, refreshToken)
	}

	private suspend fun whiteListRejected(email: String): Boolean {
		val account = accountsRepository.findAccount(email)
		return if (account != null) {
			whiteListRejected(account)
		} else {
			whiteListRepository.useWhiteList()
		}
	}

	private suspend fun whiteListRejected(userId: Long): Boolean {
		val account = accountsRepository.getAccount(userId)
		return whiteListRejected(account)
	}

	private suspend fun whiteListRejected(account: Account): Boolean {
		return !account.isAdmin &&
				whiteListRepository.useWhiteList() &&
				whiteListRepository.isOnWhiteList(account.email).not()
	}
}

fun <T> WhiteListRejected() = SResult.failure<T>(
	error = "User not on whitelist",
	displayMessage = Msg.r("api.whitelist.rejected")
)