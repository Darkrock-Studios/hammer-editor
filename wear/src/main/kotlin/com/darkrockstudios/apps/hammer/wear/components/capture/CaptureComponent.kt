package com.darkrockstudios.apps.hammer.wear.components.capture

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.update
import com.darkrockstudios.apps.hammer.common.components.ComponentBase
import com.darkrockstudios.apps.hammer.wear.data.CaptureResult
import com.darkrockstudios.apps.hammer.wear.data.CaptureTarget
import com.darkrockstudios.apps.hammer.wear.data.CaptureUseCase
import com.darkrockstudios.apps.hammer.wear.data.ListWatchProjectsUseCase
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
	private val listProjects: ListWatchProjectsUseCase,
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

		_state.update { it.copy(saving = true) }
		// Outlives the screen: a capture must land even if the watch drops the activity mid-save.
		appScope.launch {
			val result = captureUseCase.capture(target, current.text)
			withContext(dispatcherMain) {
				_state.update {
					it.copy(
						saving = false,
						outcome = when (result) {
							is CaptureResult.Saved -> Capture.Outcome.Saved(result.pending)
							CaptureResult.Empty, CaptureResult.Failed -> Capture.Outcome.Failed
						},
					)
				}
			}
		}
	}

	private suspend fun loadTargets() {
		val projects = try {
			listProjects.list().filter { it.subscribed }
		} catch (e: CancellationException) {
			throw e
		} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
			Napier.e("Failed to list the projects a note could go to", e)
			emptyList()
		}
		val lastUsed = try {
			subscriptions.lastCaptureProjectId()
		} catch (e: CancellationException) {
			throw e
		} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
			Napier.e("Failed to read the last project captured to", e)
			null
		}

		withContext(dispatcherMain) {
			targets = projects
			val default = projects.find { it.projectId == lastUsed } ?: projects.firstOrNull()
			_state.update {
				it.copy(
					projects = projects.map { project -> project.projectDef.name },
					projectName = default?.projectDef?.name,
					loading = false,
					outcome = if (projects.isEmpty()) Capture.Outcome.NoProjects else it.outcome,
				)
			}
		}
	}
}
