package com.darkrockstudios.apps.hammer.utilities

import com.github.aymanizz.ktori18n.R
import java.lang.Exception
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

typealias SResult<T> = ServerResult<T>

sealed class ServerResult<T> {
	abstract val isSuccess: Boolean
	val isFailure: Boolean
		get() = isSuccess.not()

	data class Success<T>(val data: T): ServerResult<T>() {
		override val isSuccess = true
	}

	data class Failure<T>(
		val error: String,
		val displayMessage: R?,
		val exception: Exception?
	): ServerResult<T>(){
		override val isSuccess = false
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

/**
 * Convince method that smart casts the SResult to either Success
 * or Failure
 */
@OptIn(ExperimentalContracts::class)
fun <T> isSuccess(r: ServerResult<T>): Boolean {
	contract {
		returns(true) implies (r is ServerResult.Success<T>)
		returns(false) implies (r is ServerResult.Failure<T>)
	}
	return r.isSuccess
}

/**
 * Convince method that smart casts the SResult to either Success
 * or Failure
 */
@OptIn(ExperimentalContracts::class)
fun <T> isFailure(r: ServerResult<T>): Boolean {
	contract {
		returns(false) implies (r is ServerResult.Success<T>)
		returns(true) implies (r is ServerResult.Failure<T>)
	}
	return r.isFailure
}