package com.darkrockstudios.apps.hammer.utilities

import com.github.aymanizz.ktori18n.R
import java.lang.Exception
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract


sealed class ServerResult<T> {
	abstract fun isSuccess(): Boolean

	data class Success<T>(val data: T): ServerResult<T>() {
		override fun isSuccess() = true
	}

	data class Failure<T>(
		val error: String,
		val displayMessage: R?,
		val exception: Exception?
	): ServerResult<T>(){
		override fun isSuccess() = false
	}

	companion object {
		fun <T> failure(
			error: String,
			displayMessage: R? = null,
			exception: Exception? = null
		) = Failure<T>(error, displayMessage, exception)
		fun <T> success(data: T) = Success(data)
	}
}

@OptIn(ExperimentalContracts::class)
fun <T> isSuccess(r: ServerResult<T>): Boolean {
	contract {
		returns(true) implies (r is ServerResult.Success<T>)
		returns(false) implies (r is ServerResult.Failure<T>)
	}
	return true
}