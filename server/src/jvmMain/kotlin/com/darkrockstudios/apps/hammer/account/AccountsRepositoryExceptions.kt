package com.darkrockstudios.apps.hammer.account

import com.darkrockstudios.apps.hammer.utilities.Msg

open class CreateFailed(message: String) : Exception(message)
class InvalidPassword(val result: AccountsRepository.Companion.PasswordResult) :
	CreateFailed("Invalid Password") {
	companion object {
		fun getMessage(result: AccountsRepository.Companion.PasswordResult): Msg = when (result) {
			AccountsRepository.Companion.PasswordResult.TOO_SHORT -> Msg.r("api.accounts.create.error.password.tooshort")
			AccountsRepository.Companion.PasswordResult.TOO_LONG -> Msg.r("api.accounts.create.error.password.toolong")
			AccountsRepository.Companion.PasswordResult.NO_UPPERCASE -> Msg.r("api.accounts.create.error.password.nouppercase")
			AccountsRepository.Companion.PasswordResult.NO_LOWERCASE -> Msg.r("api.accounts.create.error.password.nolowercase")
			AccountsRepository.Companion.PasswordResult.NO_NUMBER -> Msg.r("api.accounts.create.error.password.nonumber")
			AccountsRepository.Companion.PasswordResult.NO_SPECIAL -> Msg.r("api.accounts.create.error.password.nospecial")
			else -> Msg.r("api.accounts.create.error.password.generic")
		}
	}
}

class LoginFailed(message: String) : Exception(message)

class AccountNotFound(userId: Long) : Exception("User ID ($userId) not found")
