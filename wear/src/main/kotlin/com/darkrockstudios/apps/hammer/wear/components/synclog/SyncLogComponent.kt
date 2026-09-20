package com.darkrockstudios.apps.hammer.wear.components.synclog

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.components.ComponentBase
import com.darkrockstudios.apps.hammer.wear.sync.SyncCoordinator
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SyncLogComponent(
	componentContext: ComponentContext,
	private val syncCoordinator: SyncCoordinator,
) : ComponentBase(componentContext), SyncLog {

	private val _state = MutableValue(SyncLog.State(syncCoordinator.status.value.log.asReversed()))
	override val state: Value<SyncLog.State> = _state

	override fun onCreate() {
		super.onCreate()
		scope.launch {
			syncCoordinator.status.collect { status ->
				withContext(dispatcherMain) {
					_state.value = SyncLog.State(status.log.asReversed())
				}
			}
		}
	}
}
