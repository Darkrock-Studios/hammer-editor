package com.darkrockstudios.apps.hammer.common

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.Lifecycle
import com.darkrockstudios.apps.hammer.common.dependencyinjection.DISPATCHER_DEFAULT
import com.darkrockstudios.apps.hammer.common.dependencyinjection.DISPATCHER_IO
import com.darkrockstudios.apps.hammer.common.dependencyinjection.DISPATCHER_MAIN
import com.darkrockstudios.apps.hammer.common.dependencyinjection.HammerComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import org.koin.core.component.inject
import org.koin.core.qualifier.named
import kotlin.coroutines.CoroutineContext

abstract class ComponentBase(componentContext: ComponentContext) :
	ComponentContext by componentContext, Lifecycle.Callbacks, HammerComponent {

	protected val dispatcherDefault: CoroutineContext by inject(named(DISPATCHER_DEFAULT))
	protected val dispatcherIo: CoroutineContext by inject(named(DISPATCHER_IO))
	protected val dispatcherMain: CoroutineContext by inject(named(DISPATCHER_MAIN))
	protected val scope = CoroutineScope(dispatcherDefault)

	private fun setup() {
		lifecycle.subscribe(this)
	}

	override fun onDestroy() {
		scope.cancel("${this::class.simpleName} destroyed")
	}

	init {
        setup()
    }
}