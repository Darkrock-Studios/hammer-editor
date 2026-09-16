package com.darkrockstudios.apps.hammer.wear.components.capture

import com.arkivanov.decompose.value.Value

interface Capture {
	val state: Value<State>

	/** The text dictated or typed through `RemoteInput`. */
	fun onTextEntered(text: String)
	fun showProjectPicker()
	fun selectProject(projectName: String)
	fun dismissProjectPicker()
	fun save()

	enum class Mode { Note, Idea }

	data class State(
		val mode: Mode,
		val text: String = "",
		/** The projects a note can go to: subscribed ones, whose ids the server already knows. */
		val projects: List<String> = emptyList(),
		val projectName: String? = null,
		val pickingProject: Boolean = false,
		val loading: Boolean = true,
		val saving: Boolean = false,
		val outcome: Outcome? = null,
	) {
		val canSave: Boolean
			get() = text.isNotBlank() && !saving &&
				(mode == Mode.Idea || projectName != null)
	}

	sealed interface Outcome {
		/** [pending] is null until the count has been gathered, which happens after the save. */
		data class Saved(val pending: Int? = null) : Outcome

		/** No project is kept on the watch, so a note has nowhere to go. */
		data object NoProjects : Outcome
		data object Failed : Outcome
	}
}
