package com.darkrockstudios.apps.hammer.common.preview.intro

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.update
import com.darkrockstudios.apps.hammer.common.components.intro.Intro
import com.darkrockstudios.apps.hammer.common.intro.IntroUi
import com.darkrockstudios.apps.hammer.common.preview.TABLET_HEIGHT_DP
import com.darkrockstudios.apps.hammer.common.preview.TABLET_WIDTH_DP
import com.darkrockstudios.apps.hammer.common.preview.TabletPreviewSurface

/** An [Intro] that steps through slides like the real one and counts how often it was left. */
internal class FakeIntro(slide: Int = 0) : Intro {
	private val _state = MutableValue(Intro.State(slide))
	override val state: Value<Intro.State> = _state
	var left = 0
		private set

	override fun showSlide(index: Int) = _state.update { it.copy(slide = index.coerceIn(0, Intro.Slide.entries.lastIndex)) }

	override fun next() {
		if (_state.value.isLast) leave() else showSlide(_state.value.slide + 1)
	}

	override fun back() = showSlide(_state.value.slide - 1)

	override fun leave() {
		left++
	}
}

/** The first-run intro open on [slide]. */
@Composable
fun ScreenIntroUiTabletPreview(slide: Intro.Slide = Intro.Slide.Structure) {
	val component = remember(slide) { FakeIntro(slide.ordinal) }
	TabletPreviewSurface {
		IntroUi(component)
	}
}

@Preview(widthDp = TABLET_WIDTH_DP, heightDp = TABLET_HEIGHT_DP)
@Composable
private fun IntroUiPreview() {
	ScreenIntroUiTabletPreview()
}
