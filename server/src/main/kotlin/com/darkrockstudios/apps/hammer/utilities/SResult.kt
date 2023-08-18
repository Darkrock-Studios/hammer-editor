package com.darkrockstudios.apps.hammer.utilities

import com.github.aymanizz.ktori18n.DEFAULT_RESOURCE_BUNDLE
import com.github.aymanizz.ktori18n.R
import com.github.aymanizz.ktori18n.t
import io.ktor.server.application.*
import org.jetbrains.annotations.PropertyKey
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

typealias SResult<T> = ServerResult<T>

sealed class ServerResult<out T> {
	abstract val isSuccess: Boolean
	val isFailure: Boolean
		get() = isSuccess.not()

	data class Success<T>(val data: T) : ServerResult<T>() {
		override val isSuccess = true
	}

	data class Failure<T>(
		val error: String,
		val displayMessage: Msg?,
		val exception: Throwable?
	) : ServerResult<T>() {
		override val isSuccess = false

		fun displayMessageText(call: ApplicationCall, default: R): String {
			return displayMessage?.text(call) ?: call.t(default)
		}

		fun displayMessageText(call: ApplicationCall): String? {
			return displayMessage?.text(call)
		}
	}

	companion object {
		fun <T> failure(
			error: String,
			displayMessage: Msg? = null,
			exception: Throwable? = null
		) = Failure<T>(error, displayMessage, exception)

		fun <T> failure(
			exception: Throwable
		) = Failure<T>(exception.toString(), null, exception)


		fun <T> success(data: T) = Success(data)

		fun success() = Success(Unit)
	}
}

typealias Msg = ServerMessage
class ServerMessage private constructor(
	@PropertyKey(resourceBundle = DEFAULT_RESOURCE_BUNDLE) res: R,
	inArgs: List<Any>
) {
	val r: R = res
	val args: List<Any> = inArgs

	fun text(call: ApplicationCall): String = call.t(r, args)

	companion object {
		fun r(key: String, vararg args: Any): ServerMessage = ServerMessage(R(key), args.toList())
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