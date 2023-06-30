package com.darkrockstudios.apps.hammer.common.components

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.Lifecycle
import com.darkrockstudios.apps.hammer.common.dependencyinjection.HammerComponent
import com.darkrockstudios.apps.hammer.common.dependencyinjection.injectDefaultDispatcher
import com.darkrockstudios.apps.hammer.common.dependencyinjection.injectIoDispatcher
import com.darkrockstudios.apps.hammer.common.dependencyinjection.injectMainDispatcher
import com.darkrockstudios.apps.hammer.common.util.lifecycleCoroutineScope
import kotlinx.coroutines.cancel

abstract class ComponentBase(componentContext: ComponentContext) :
	ComponentContext by componentContext, Lifecycle.Callbacks, HammerComponent {

	protected val dispatcherDefault by injectDefaultDispatcher()
	protected val dispatcherIo by injectIoDispatcher()
	protected val dispatcherMain by injectMainDispatcher()
	protected val scope = lifecycleCoroutineScope(dispatcherDefault)

	private fun setup() {
		lifecycle.subscribe(this)
	}

	override fun onDestroy() {
		super.onDestroy()
		scope.cancel("Destroyed")
	}

	init {
		setup()
	}
}

