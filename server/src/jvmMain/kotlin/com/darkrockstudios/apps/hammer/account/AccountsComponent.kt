package com.darkrockstudios.apps.hammer.account

import com.darkrockstudios.apps.hammer.Account
import com.darkrockstudios.apps.hammer.admin.WhiteListRepository
import com.darkrockstudios.apps.hammer.base.http.Token
import com.darkrockstudios.apps.hammer.projects.ProjectsRepository
import com.darkrockstudios.apps.hammer.utilities.Msg
import com.darkrockstudios.apps.hammer.utilities.SResult
import com.darkrockstudios.apps.hammer.utilities.ServerResult
import com.darkrockstudios.apps.hammer.utilities.isSuccess

class AccountsComponent(
	private val accountsRepository: AccountsRepository,
	private val whiteListRepository: WhiteListRepository,
	private val projectsRepository: ProjectsRepository,
) {
	suspend fun createAccount(
		email: String,
		installId: String,
		password: String
	): ServerResult<Token> {
		// If we dont have users, skip whitelist check
		if (accountsRepository.hasUsers() && checkIfWhiteListRejected(email))
			return ServerResult.failure(
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
		if (checkIfWhiteListRejected(email)) return WhiteListRejected()

		return accountsRepository.login(email, password, installId)
	}

	suspend fun refreshToken(
		userId: Long,
		installId: String,
		refreshToken: String
	): SResult<Token> {
		if (checkIfWhiteListRejected(userId)) return WhiteListRejected()

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

	private suspend fun checkIfWhiteListRejected(userId: Long): Boolean {
		val account = accountsRepository.getAccount(userId)
		return checkIfWhiteListRejected(account)
	}

	private suspend fun checkIfWhiteListRejected(account: Account): Boolean {
		return !account.is_admin &&
			whiteListRepository.useWhiteList() &&
			whiteListRepository.isOnWhiteList(account.email).not()
	}
}

fun <T> WhiteListRejected() = SResult.failure<T>(
	error = "User not on whitelist",
	displayMessage = Msg.r("api.whitelist.rejected")
)