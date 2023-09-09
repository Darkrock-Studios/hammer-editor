package com.darkrockstudios.apps.hammer.common.components

import com.darkrockstudios.apps.hammer.common.data.Msg
import com.darkrockstudios.apps.hammer.common.dependencyinjection.injectMainDispatcher
import dev.icerock.moko.resources.StringResource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent

interface ComponentToaster {
	val toast: Flow<ToastMessage>

	fun showToast(scope: CoroutineScope, message: StringResource, vararg params: Any)
	fun showToast(scope: CoroutineScope, message: Msg)
	suspend fun showToast(message: StringResource, vararg params: Any)
	suspend fun showToast(message: Msg)
}

class ComponentToasterImpl : ComponentToaster, KoinComponent {
	private val mainDispatcher by injectMainDispatcher()

	private val _toast = MutableSharedFlow<ToastMessage>()
	override val toast: Flow<ToastMessage> = _toast

	override fun showToast(scope: CoroutineScope, message: StringResource, vararg params: Any) {
		scope.launch(mainDispatcher) {
			_toast.emit(ToastMessage(message, params))
		}
	}

	override fun showToast(scope: CoroutineScope, message: Msg) {
		showToast(scope, message.r, *message.args)
	}

	override suspend fun showToast(message: StringResource, vararg params: Any) {
		_toast.emit(ToastMessage(message, params))
	}

	override suspend fun showToast(message: Msg) {
		showToast(message.r, *message.args)
	}
}

data class ToastMessage(
	val stringResource: StringResource,
	val params: Array<out Any>
) {
	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (other == null || this::class != other::class) return false

		other as ToastMessage

		if (stringResource != other.stringResource) return false
		if (!params.contentEquals(other.params)) return false

		return true
	}

	override fun hashCode(): Int {
		var result = stringResource.hashCode()
		result = 31 * result + params.contentHashCode()
		return result
	}
}