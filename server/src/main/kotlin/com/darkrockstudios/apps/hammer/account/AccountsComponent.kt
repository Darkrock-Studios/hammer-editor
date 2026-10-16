package com.darkrockstudios.apps.hammer.account

import com.darkrockstudios.apps.hammer.Account
import com.darkrockstudios.apps.hammer.account.mfa.ChallengeResult
import com.darkrockstudios.apps.hammer.account.mfa.CodeResult
import com.darkrockstudios.apps.hammer.account.mfa.MfaRepository
import com.darkrockstudios.apps.hammer.admin.WhiteListRepository
import com.darkrockstudios.apps.hammer.base.http.MfaChallenge
import com.darkrockstudios.apps.hammer.base.http.TermsOfServiceChallenge
import com.darkrockstudios.apps.hammer.base.http.Token
import com.darkrockstudios.apps.hammer.plugin.PluginRegistry
import com.darkrockstudios.apps.hammer.projects.ProjectsRepository
import com.darkrockstudios.apps.hammer.utilities.Msg
import com.darkrockstudios.apps.hammer.utilities.SResult
import com.darkrockstudios.apps.hammer.utilities.ServerResult
import com.darkrockstudios.apps.hammer.utilities.isSuccess
import kotlin.time.Instant

class AccountsComponent(
	private val accountsRepository: AccountsRepository,
	private val whiteListRepository: WhiteListRepository,
	private val projectsRepository: ProjectsRepository,
	private val termsOfServiceRepository: TermsOfServiceRepository,
	private val mfaRepository: MfaRepository,
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

	/** A [code] answers the second factor inline, for clients that can't do the challenge round trip. */
	suspend fun login(email: String, password: String, installId: String, code: String? = null): LoginResult {
		if (checkIfWhiteListRejected(email)) {
			return LoginResult.Failure(whiteListRejectedFailure())
		}

		val verified = accountsRepository.verifyPassword(email, password)
		if (!isSuccess(verified)) return LoginResult.Failure((verified as ServerResult.Failure).retype())
		val account = verified.data

		if (!mfaRepository.hasMfa(account.id)) {
			return LoginResult.Success(accountsRepository.issueToken(account.id, installId))
		}
		if (code == null) {
			mfaRepository.lockedUntil(account.id)?.let { return LoginResult.MfaLocked(account.email, it) }
			return LoginResult.MfaRequired(mfaRepository.createChallenge(account.id, installId))
		}
		return when (val result = mfaRepository.verifyCode(account.id, code)) {
			CodeResult.Verified -> LoginResult.Success(accountsRepository.issueToken(account.id, installId))
			CodeResult.Invalid -> LoginResult.MfaCodeInvalid(account.email)
			is CodeResult.Locked -> LoginResult.MfaLocked(account.email, result.until)
		}
	}

	suspend fun loginMfa(mfaToken: String, code: String, installId: String): LoginResult {
		val userId = when (val result = mfaRepository.verifyChallenge(mfaToken, installId, code)) {
			ChallengeResult.Invalid -> return LoginResult.MfaCodeInvalid(null)
			is ChallengeResult.InvalidCode ->
				return LoginResult.MfaCodeInvalid(accountsRepository.getAccountOrNull(result.userId)?.email)

			is ChallengeResult.Locked ->
				return LoginResult.MfaLocked(accountsRepository.getAccountOrNull(result.userId)?.email, result.until)

			is ChallengeResult.Verified -> result.userId
		}

		// The account may have changed in the minutes since the password was checked.
		val account = accountsRepository.getAccountOrNull(userId)
			?: return LoginResult.MfaCodeInvalid(null)
		if (account.deleted_at != null) {
			return LoginResult.Failure(
				SResult.failure(
					"Account pending deletion",
					Msg.r("api_accounts_login_error_pending_deletion"),
					AccountPendingDeletion()
				)
			)
		}
		if (checkIfWhiteListRejected(account)) {
			return LoginResult.Failure(whiteListRejectedFailure())
		}
		return LoginResult.Success(accountsRepository.issueToken(userId, installId))
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

	suspend fun pairInstall(
		userId: Long,
		callerInstallId: String,
		newInstallId: String,
	): SResult<Token> {
		val account = accountsRepository.getAccountOrNull(userId)
			?: return SResult.failure("Account not found", Msg.r("api_accounts_login_error_notoken"))
		if (account.deleted_at != null) {
			return SResult.failure(
				"Account pending deletion",
				Msg.r("api_accounts_login_error_pending_deletion")
			)
		}
		if (checkIfWhiteListRejected(account)) {
			return whiteListRejectedFailure()
		}

		return accountsRepository.pairInstall(userId, callerInstallId, newInstallId)
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

sealed interface LoginResult {
	data class Success(val token: Token) : LoginResult
	data class MfaRequired(val challenge: MfaChallenge) : LoginResult

	/** [email] is the account the code was for, when known, for the login audit. */
	data class MfaCodeInvalid(val email: String?) : LoginResult

	/** Too many wrong codes; the account takes no code until [until]. */
	data class MfaLocked(val email: String?, val until: Instant) : LoginResult
	data class Failure(val failure: ServerResult.Failure<Token>) : LoginResult
}

private fun ServerResult.Failure<*>.retype(): ServerResult.Failure<Token> =
	ServerResult.Failure(error, displayMessage, exception)

sealed interface CreateAccountResult {
	data class Success(val token: Token) : CreateAccountResult
	data class TermsRequired(val challenge: TermsOfServiceChallenge) : CreateAccountResult
	data class Failure(val failure: ServerResult.Failure<Token>) : CreateAccountResult
}