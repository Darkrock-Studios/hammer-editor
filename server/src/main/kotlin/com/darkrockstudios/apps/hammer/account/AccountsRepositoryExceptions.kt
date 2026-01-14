package com.darkrockstudios.apps.hammer.account

import com.darkrockstudios.apps.hammer.base.validate.PasswordValidationResult
import com.darkrockstudios.apps.hammer.utilities.Msg

open class CreateFailed(message: String) : Exception(message)
class InvalidPassword(val result: PasswordValidationResult) :
	CreateFailed("Invalid Password") {
	companion object {
		fun getMessage(result: PasswordValidationResult): Msg = when (result) {
			PasswordValidationResult.TOO_SHORT -> Msg.r("api_accounts_create_error_password_tooshort")
			PasswordValidationResult.TOO_LONG -> Msg.r("api_accounts_create_error_password_toolong")
			PasswordValidationResult.NO_UPPERCASE -> Msg.r("api_accounts_create_error_password_nouppercase")
			PasswordValidationResult.NO_LOWERCASE -> Msg.r("api_accounts_create_error_password_nolowercase")
			PasswordValidationResult.NO_NUMBER -> Msg.r("api_accounts_create_error_password_nonumber")
			PasswordValidationResult.NO_SPECIAL -> Msg.r("api_accounts_create_error_password_nospecial")
			else -> Msg.r("api_accounts_create_error_password_generic")
		}
	}
}

class LoginFailed(message: String) : Exception(message)

class AccountNotFound(userId: Long) : Exception("User ID ($userId) not found")
