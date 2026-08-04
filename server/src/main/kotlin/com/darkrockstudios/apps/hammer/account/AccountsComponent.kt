package com.darkrockstudios.apps.hammer.account

import com.darkrockstudios.apps.hammer.Account
import com.darkrockstudios.apps.hammer.admin.WhiteListRepository
import com.darkrockstudios.apps.hammer.base.http.TermsOfServiceChallenge
import com.darkrockstudios.apps.hammer.base.http.Token
import com.darkrockstudios.apps.hammer.plugin.PluginRegistry
import com.darkrockstudios.apps.hammer.projects.ProjectsRepository
import com.darkrockstudios.apps.hammer.utilities.Msg
import com.darkrockstudios.apps.hammer.utilities.SResult
import com.darkrockstudios.apps.hammer.utilities.ServerResult
import com.darkrockstudios.apps.hammer.utilities.isSuccess

class AccountsComponent(
	private val accountsRepository: AccountsRepository,
	private val whiteListRepository: WhiteListRepository,
	private val projectsRepository: ProjectsRepository,
	private val termsOfServiceRepository: TermsOfServiceRepository,
	private val pluginRegistry: PluginRegistry,
) {
	suspend fun createAccount(
		email: String,
		installId: String,
		password: String,
		acceptedTosVersion: String? = null,
	): CreateAccountResult {
		// If we dont have users, skip whitelist check
		if (accountsRepository.hasUsers() && checkIfWhiteListRejected(email)) {
			return CreateAccountResult.Failure(whiteListRejectedFailure())
		}

		// A single read closes the enforce/accept race: if a challenge exists and the submitted
		// version doesn't match it, the account is never created.
		val challenge = termsOfServiceRepository.challenge()
		if (challenge != null && acceptedTosVersion != challenge.version) {
			return CreateAccountResult.TermsRequired(challenge)
		}

		val result = accountsRepository.createAccount(email, installId, password)
		return if (isSuccess(result)) {
			val token = result.data
			projectsRepository.createUserData(token.userId)
			CreateAccountResult.Success(token)
		} else {
			CreateAccountResult.Failure(result as ServerResult.Failure<Token>)
		}
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
		if (account?.deleted_at != null) {
			// Real tokens were revoked at soft delete, so anyone reaching this
			// held a valid refresh token; the explicit message is safe.
			return SResult.failure(
				"Account pending deletion",
				Msg.r("api_accounts_login_error_pending_deletion")
			)
		}
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
			whiteListRepository.isOnWhiteList(email).not()
		}
	}

	private suspend fun checkIfWhiteListRejected(account: Account): Boolean {
		return !account.is_admin &&
			whiteListRepository.isOnWhiteList(account.email).not()
	}

	private suspend fun whiteListRejectedFailure(): ServerResult.Failure<Token> {
		val message = pluginRegistry.activeAllowedUsersSource()?.rejectionMessage()
		return SResult.failure(
			error = "User not on whitelist",
			displayMessage = message ?: Msg.r("api_allowedusers_rejected"),
			exception = NotWhitelisted()
		)
	}
}

sealed interface CreateAccountResult {
	data class Success(val token: Token) : CreateAccountResult
	data class TermsRequired(val challenge: TermsOfServiceChallenge) : CreateAccountResult
	data class Failure(val failure: ServerResult.Failure<Token>) : CreateAccountResult
}