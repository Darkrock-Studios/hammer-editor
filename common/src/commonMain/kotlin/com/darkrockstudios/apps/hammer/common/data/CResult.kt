package com.darkrockstudios.apps.hammer.common.data

import com.darkrockstudios.apps.hammer.common.util.StrRes
import org.jetbrains.compose.resources.StringResource
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

		suspend fun displayMessageText(strRes: StrRes, default: StringResource): String {
			return displayMessage?.text(strRes) ?: strRes.get(default)
		}

		suspend fun displayMessageText(strRes: StrRes): String? {
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

/**
 * A message that can be either a StringResource (for client-side localization)
 * or a raw string (for pre-localized server messages).
 */
sealed class ClientMessage {
	abstract suspend fun text(strRes: StrRes): String

	/**
	 * A message backed by a StringResource for client-side localization.
	 */
	class Resource private constructor(
		private val res: StringResource,
		private val args: Array<out Any>
	) : ClientMessage() {
		override suspend fun text(strRes: StrRes): String = strRes.get(res, *args)

		fun getStringResource(): StringResource = res
		fun getArgs(): Array<out Any> = args

		companion object {
			operator fun invoke(res: StringResource, vararg args: Any): Resource = Resource(res, args)
		}
	}

	/**
	 * A message with a literal string (typically pre-localized from the server).
	 */
	class Literal(private val message: String) : ClientMessage() {
		override suspend fun text(strRes: StrRes): String = message

		/** Get the literal message directly without StrRes (since no localization needed) */
		fun text(): String = message
	}
}

fun StringResource.toMsg(): Msg = ClientMessage.Resource(this)

fun String.toMsg(): Msg = ClientMessage.Literal(this)

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