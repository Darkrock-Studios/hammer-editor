package com.darkrockstudios.apps.hammer.common.components.intro

import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.intro_anywhere_body
import com.darkrockstudios.apps.hammer.intro_anywhere_title
import com.darkrockstudios.apps.hammer.intro_example_body
import com.darkrockstudios.apps.hammer.intro_example_title
import com.darkrockstudios.apps.hammer.intro_files_body
import com.darkrockstudios.apps.hammer.intro_files_title
import com.darkrockstudios.apps.hammer.intro_welcome_body
import com.darkrockstudios.apps.hammer.intro_structure_body
import com.darkrockstudios.apps.hammer.intro_structure_title
import com.darkrockstudios.apps.hammer.intro_world_body
import com.darkrockstudios.apps.hammer.intro_world_title
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.StringResource

/** The first-run slideshow, shown once before the project list. */
interface Intro {
	val state: Value<State>

	fun showSlide(index: Int)
	fun next()
	fun back()

	/** Leaves the intro for good, whether skipped or finished. */
	fun leave()

	@Serializable
	data class State(val slide: Int = 0) {
		val isFirst: Boolean get() = slide == 0
		val isLast: Boolean get() = slide == Slide.entries.lastIndex
	}

	enum class Slide(val title: StringResource?, val body: StringResource) {
		Welcome(title = null, Res.string.intro_welcome_body),
		Structure(Res.string.intro_structure_title, Res.string.intro_structure_body),
		World(Res.string.intro_world_title, Res.string.intro_world_body),
		Files(Res.string.intro_files_title, Res.string.intro_files_body),
		Anywhere(Res.string.intro_anywhere_title, Res.string.intro_anywhere_body),
		Example(Res.string.intro_example_title, Res.string.intro_example_body),
		;

		/** A hero slide has no [title]: its drawing carries the heading and takes most of the screen. */
		val hero: Boolean get() = title == null
	}
}
