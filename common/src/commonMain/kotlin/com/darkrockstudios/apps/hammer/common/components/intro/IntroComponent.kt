package com.darkrockstudios.apps.hammer.common.components.intro

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.update
import com.arkivanov.essenty.backhandler.BackCallback
import com.darkrockstudios.apps.hammer.common.components.ComponentBase
import com.darkrockstudios.apps.hammer.common.data.nux.NuxRepository
import org.koin.core.component.inject

class IntroComponent(
	componentContext: ComponentContext,
	private val onDone: () -> Unit,
) : Intro, ComponentBase(componentContext) {

	private val nuxRepository: NuxRepository by inject()

	private val _state = MutableValue(stateKeeper.consume(STATE_KEY, Intro.State.serializer()) ?: Intro.State())
	override val state: Value<Intro.State> = _state

	private val backCallback = BackCallback { back() }

	init {
		stateKeeper.register(STATE_KEY, Intro.State.serializer()) { _state.value }
		backHandler.register(backCallback)
		_state.subscribe { backCallback.isEnabled = !it.isFirst }
	}

	override fun showSlide(index: Int) {
		val slide = index.coerceIn(0, Intro.Slide.entries.lastIndex)
		_state.update { it.copy(slide = slide) }
	}

	override fun next() {
		if (_state.value.isLast) leave() else showSlide(_state.value.slide + 1)
	}

	override fun back() = showSlide(_state.value.slide - 1)

	override fun leave() {
		nuxRepository.markIntroShown()
		onDone()
	}

	private companion object {
		const val STATE_KEY = "intro_state"
	}
}
