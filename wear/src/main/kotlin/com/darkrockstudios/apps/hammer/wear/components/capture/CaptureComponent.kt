package com.darkrockstudios.apps.hammer.wear.components.capture

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.update
import com.darkrockstudios.apps.hammer.common.components.ComponentBase
import com.darkrockstudios.apps.hammer.wear.data.CaptureResult
import com.darkrockstudios.apps.hammer.wear.data.CaptureTarget
import com.darkrockstudios.apps.hammer.wear.data.CaptureTargets
import com.darkrockstudios.apps.hammer.wear.data.CaptureTargetsUseCase
import com.darkrockstudios.apps.hammer.wear.data.CaptureUseCase
import com.darkrockstudios.apps.hammer.wear.data.SubscribedProjectsRepository
import com.darkrockstudios.apps.hammer.wear.data.WatchProject
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CaptureComponent(
	componentContext: ComponentContext,
	private val mode: Capture.Mode,
	private val startWithPicker: Boolean = false,
	private val captureTargets: CaptureTargetsUseCase,
	private val subscriptions: SubscribedProjectsRepository,
	private val captureUseCase: CaptureUseCase,
	private val appScope: CoroutineScope,
) : ComponentBase(componentContext), Capture {

	private val _state = MutableValue(Capture.State(mode = mode))
	override val state: Value<Capture.State> = _state

	// Only touched on the main dispatcher.
	private var targets: List<WatchProject> = emptyList()

	override fun onCreate() {
		super.onCreate()
		if (mode == Capture.Mode.Idea) {
			_state.update { it.copy(loading = false) }
		} else {
			scope.launch { loadTargets() }
		}
	}

	override fun onTextEntered(text: String) {
		_state.update { it.copy(text = text.trim()) }
	}

	override fun showProjectPicker() {
		if (targets.isEmpty()) return
		_state.update { it.copy(pickingProject = true) }
	}

	override fun selectProject(projectName: String) {
		val target = targets.find { it.projectDef.name == projectName } ?: return
		_state.update { it.copy(projectName = projectName, pickingProject = false) }
		target.projectId?.let { projectId ->
			appScope.launch { subscriptions.setLastCaptureProject(projectId) }
		}
	}

	override fun dismissProjectPicker() {
		_state.update { it.copy(pickingProject = false) }
	}

	override fun requestDiscard() {
		if (!_state.value.hasUnsavedWork) return
		_state.update { it.copy(confirmingDiscard = true, pickingProject = false) }
	}

	override fun cancelDiscard() {
		_state.update { it.copy(confirmingDiscard = false) }
	}

	override fun save() {
		val current = _state.value
		if (!current.canSave) return

		val target = when (current.mode) {
			Capture.Mode.Idea -> CaptureTarget.Idea
			Capture.Mode.Note -> {
				val projectDef = targets.find { it.projectDef.name == current.projectName }?.projectDef ?: return
				CaptureTarget.Note(projectDef)
			}
		}

		_state.update { it.copy(saving = true, confirmingDiscard = false) }
		// Outlives the screen: a capture must land even if the watch drops the activity mid-save.
		appScope.launch {
			val result = captureUseCase.capture(target, current.text)
			withContext(dispatcherMain) {
				_state.update {
					it.copy(
						saving = false,
						outcome = when (result) {
							CaptureResult.Saved -> Capture.Outcome.Saved()
							CaptureResult.Empty, CaptureResult.Failed -> Capture.Outcome.Failed
						},
					)
				}
			}

			if (result != CaptureResult.Saved) return@launch

			// The user has already been told their words are safe; the count only fills in the rest.
			val pending = captureUseCase.pendingCount() ?: return@launch
			withContext(dispatcherMain) {
				_state.update {
					if (it.outcome is Capture.Outcome.Saved) {
						it.copy(outcome = Capture.Outcome.Saved(pending))
					} else {
						it
					}
				}
			}
		}
	}

	private suspend fun loadTargets() {
		val loaded = try {
			captureTargets.load()
		} catch (e: CancellationException) {
			throw e
		} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
			Napier.e("Failed to list the projects a note could go to", e)
			CaptureTargets()
		}

		withContext(dispatcherMain) {
			targets = loaded.projects
			_state.update {
				it.copy(
					projects = loaded.projects.map { project -> project.projectDef.name },
					projectName = loaded.default?.projectDef?.name,
					// Only worth showing when there is a choice to make.
					pickingProject = startWithPicker && loaded.projects.size > 1,
					loading = false,
					outcome = if (loaded.projects.isEmpty()) Capture.Outcome.NoProjects else it.outcome,
				)
			}
		}
	}
}
