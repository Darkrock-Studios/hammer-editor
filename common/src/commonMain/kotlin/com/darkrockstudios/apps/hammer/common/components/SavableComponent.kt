package com.darkrockstudios.apps.hammer.common.components

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import kotlinx.serialization.KSerializer
import org.koin.mp.KoinPlatformTools

abstract class SavableComponent<S : Any>(componentContext: ComponentContext) : ComponentBase(componentContext) {

	abstract val state: Value<S>

	abstract fun getStateSerializer(): KSerializer<S>

	override fun onCreate() {

		super.onCreate()
		stateKeeper.register(this::class.simpleName!!, getStateSerializer()) { state.value }
	}

	override fun onDestroy() {
		super.onDestroy()
		stateKeeper.unregister(this::class.simpleName!!)
	}
}

inline fun <reified S : Any> SavableComponent<S>.savableState(
	crossinline newState: () -> S,
): Lazy<MutableValue<S>> =
	lazy(KoinPlatformTools.defaultLazyMode()) {
		MutableValue(
			stateKeeper.consume(
				key = this::class.simpleName!!,
				strategy = getStateSerializer(),
			) ?: newState()
		)
	}