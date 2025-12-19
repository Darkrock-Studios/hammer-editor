package com.darkrockstudios.apps.hammer.account

import com.darkrockstudios.apps.hammer.utilities.Msg

open class CreateFailed(message: String) : Exception(message)
class InvalidPassword(val result: AccountsRepository.Companion.PasswordResult) :
	CreateFailed("Invalid Password") {
	companion object {
		fun getMessage(result: AccountsRepository.Companion.PasswordResult): Msg = when (result) {
			AccountsRepository.Companion.PasswordResult.TOO_SHORT -> Msg.r("api_accounts_create_error_password_tooshort")
			AccountsRepository.Companion.PasswordResult.TOO_LONG -> Msg.r("api_accounts_create_error_password_toolong")
			AccountsRepository.Companion.PasswordResult.NO_UPPERCASE -> Msg.r("api_accounts_create_error_password_nouppercase")
			AccountsRepository.Companion.PasswordResult.NO_LOWERCASE -> Msg.r("api_accounts_create_error_password_nolowercase")
			AccountsRepository.Companion.PasswordResult.NO_NUMBER -> Msg.r("api_accounts_create_error_password_nonumber")
			AccountsRepository.Companion.PasswordResult.NO_SPECIAL -> Msg.r("api_accounts_create_error_password_nospecial")
			else -> Msg.r("api_accounts_create_error_password_generic")
		}
	}
}

class LoginFailed(message: String) : Exception(message)

class AccountNotFound(userId: Long) : Exception("User ID ($userId) not found")
