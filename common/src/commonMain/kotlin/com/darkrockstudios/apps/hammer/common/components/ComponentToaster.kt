package com.darkrockstudios.apps.hammer.common.components

import com.darkrockstudios.apps.hammer.common.data.ClientMessage
import com.darkrockstudios.apps.hammer.common.data.Msg
import com.darkrockstudios.apps.hammer.common.dependencyinjection.injectMainDispatcher
import com.darkrockstudios.apps.hammer.common.util.StrRes
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.koin.core.component.KoinComponent

interface ComponentToaster {
	val toast: Flow<ToastMessage>

	fun showToast(scope: CoroutineScope, message: StringResource, vararg params: Any)
	fun showToast(scope: CoroutineScope, message: String)
	fun showToast(scope: CoroutineScope, message: Msg)
	suspend fun showToast(message: StringResource, vararg params: Any)
	suspend fun showToast(message: String)
	suspend fun showToast(message: Msg)
}

class ComponentToasterImpl : ComponentToaster, KoinComponent {
	private val mainDispatcher by injectMainDispatcher()

	private val _toast = MutableSharedFlow<ToastMessage>()
	override val toast: Flow<ToastMessage> = _toast

	override fun showToast(scope: CoroutineScope, message: StringResource, vararg params: Any) {
		scope.launch(mainDispatcher) {
			_toast.emit(ToastMessage.Resource(message, *params))
		}
	}

	override fun showToast(scope: CoroutineScope, message: String) {
		scope.launch(mainDispatcher) {
			_toast.emit(ToastMessage.Literal(message))
		}
	}

	override fun showToast(scope: CoroutineScope, message: Msg) {
		when (message) {
			is ClientMessage.Resource -> showToast(scope, message.getStringResource(), *message.getArgs())
			is ClientMessage.Literal -> scope.launch(mainDispatcher) {
				_toast.emit(ToastMessage.Literal(message.text()))
			}
		}
	}

	override suspend fun showToast(message: StringResource, vararg params: Any) {
		_toast.emit(ToastMessage.Resource(message, *params))
	}

	override suspend fun showToast(message: String) {
		Napier.d { "ABROWN: showToast Literal: $message" }
		_toast.emit(ToastMessage.Literal(message))
	}

	override suspend fun showToast(message: Msg) {
		when (message) {
			is ClientMessage.Resource -> showToast(message.getStringResource(), *message.getArgs())
			is ClientMessage.Literal -> _toast.emit(ToastMessage.Literal(message.text()))
		}
	}
}

/**
 * A toast message that can be either a StringResource (for localization)
 * or a literal string.
 */
sealed class ToastMessage {
	abstract suspend fun text(strRes: StrRes): String

	class Resource private constructor(
		private val stringResource: StringResource,
		private val params: Array<out Any>
	) : ToastMessage() {
		override suspend fun text(strRes: StrRes): String = strRes.get(stringResource, *params)

		override fun equals(other: Any?): Boolean {
			if (this === other) return true
			if (other == null || this::class != other::class) return false
			other as Resource
			if (stringResource != other.stringResource) return false
			if (!params.contentEquals(other.params)) return false
			return true
		}

		override fun hashCode(): Int {
			var result = stringResource.hashCode()
			result = 31 * result + params.contentHashCode()
			return result
		}

		companion object {
			operator fun invoke(stringResource: StringResource, vararg params: Any): Resource =
				Resource(stringResource, params)
		}
	}

	class Literal(private val message: String) : ToastMessage() {
		override suspend fun text(strRes: StrRes): String = message

		override fun equals(other: Any?): Boolean {
			if (this === other) return true
			if (other == null || this::class != other::class) return false
			other as Literal
			return message == other.message
		}

		override fun hashCode(): Int = message.hashCode()
	}
}
