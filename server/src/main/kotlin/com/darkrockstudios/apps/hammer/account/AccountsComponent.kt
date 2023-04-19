package com.darkrockstudios.apps.hammer.account

import com.darkrockstudios.apps.hammer.Account
import com.darkrockstudios.apps.hammer.admin.WhiteListRepository
import com.darkrockstudios.apps.hammer.base.http.Token
import com.darkrockstudios.apps.hammer.projects.ProjectsRepository

class AccountsComponent(
	private val accountsRepository: AccountsRepository,
	private val whiteListRepository: WhiteListRepository,
	private val projectsRepository: ProjectsRepository,
) {
	suspend fun createAccount(email: String, installId: String, password: String): Result<Token> {
		if (whiteListRejected(email)) return Result.failure(WhiteListException())

		val result = accountsRepository.createAccount(email, installId, password)
		if (result.isSuccess) {
			val token = result.getOrThrow()
			projectsRepository.createUserData(token.userId)
		}

		return result
	}

	suspend fun login(email: String, password: String, installId: String): Result<Token> {
		if (whiteListRejected(email)) return Result.failure(WhiteListException())

		return if (whiteListRepository.useWhiteList().not() || whiteListRepository.isOnWhiteList(email)) {
			accountsRepository.login(email, password, installId)
		} else {
			Result.failure(WhiteListException())
		}
	}

	suspend fun refreshToken(userId: Long, installId: String, refreshToken: String): Result<Token> {
		if (whiteListRejected(userId)) return Result.failure(WhiteListException())

		return accountsRepository.refreshToken(userId, installId, refreshToken)
	}

	private suspend fun whiteListRejected(email: String): Boolean {
		val account = accountsRepository.findAccount(email)
		return if (account != null) {
			whiteListRejected(account)
		} else {
			false
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