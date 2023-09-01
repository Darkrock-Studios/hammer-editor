package com.darkrockstudios.apps.hammer.common.data

import com.darkrockstudios.apps.hammer.common.util.StrRes
import dev.icerock.moko.resources.StringResource
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

typealias CResult<T> = ClientResult<T>

sealed class ClientResult<out T> {

	abstract val isSuccess: Boolean
	val isFailure: Boolean
		get() = isSuccess.not()

	data class Success<T>(val data: T) : ClientResult<T>() {
		override val isSuccess = true
	}

	data class Failure<T>(
		val error: String,
		val displayMessage: Msg?,
		val exception: Throwable?
	) : ClientResult<T>() {
		override val isSuccess = false

		fun displayMessageText(strRes: StrRes, default: StringResource): String {
			return displayMessage?.text(strRes) ?: strRes.get(default)
		}

		fun displayMessageText(strRes: StrRes): String? {
			return displayMessage?.text(strRes)
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

typealias Msg = ClientMessage

class ClientMessage private constructor(
	res: StringResource,
	inArgs: List<Any>
) {
	val r: StringResource = res
	val args: Array<Any> = inArgs.toTypedArray()

	fun text(strRes: StrRes): String = strRes.get(r, *args)
}

/**
 * Convince method that smart casts the SResult to either Success
 * or Failure
 */
@OptIn(ExperimentalContracts::class)
fun <T> isSuccess(r: ClientResult<T>): Boolean {
	contract {
		returns(true) implies (r is ClientResult.Success<T>)
		returns(false) implies (r is ClientResult.Failure<T>)
	}
	return r.isSuccess
}

/**
 * Convince method that smart casts the SResult to either Success
 * or Failure
 */
@OptIn(ExperimentalContracts::class)
fun <T> isFailure(r: ClientResult<T>): Boolean {
	contract {
		returns(false) implies (r is ClientResult.Success<T>)
		returns(true) implies (r is ClientResult.Failure<T>)
	}
	return r.isFailure
}