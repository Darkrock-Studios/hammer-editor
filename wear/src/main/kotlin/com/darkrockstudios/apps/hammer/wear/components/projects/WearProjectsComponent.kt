package com.darkrockstudios.apps.hammer.wear.components.projects

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.update
import com.darkrockstudios.apps.hammer.common.components.ComponentBase
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.wear.data.ListWatchProjectsUseCase
import com.darkrockstudios.apps.hammer.wear.data.SignOutUseCase
import com.darkrockstudios.apps.hammer.wear.data.SubscribedProjectsRepository
import com.darkrockstudios.apps.hammer.wear.data.WatchProject
import com.darkrockstudios.apps.hammer.wear.sync.SyncCoordinator
import com.darkrockstudios.apps.hammer.wear.sync.SyncStatus
import com.darkrockstudios.apps.hammer.wear.sync.SyncTrigger
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WearProjectsComponent(
	componentContext: ComponentContext,
	globalSettingsStore: GlobalSettingsStore,
	private val listProjects: ListWatchProjectsUseCase,
	private val projectsRepository: ProjectsRepository,
	private val subscriptions: SubscribedProjectsRepository,
	private val syncCoordinator: SyncCoordinator,
	private val signOutUseCase: SignOutUseCase,
	private val appScope: CoroutineScope,
	private val onShowSyncLog: () -> Unit,
) : ComponentBase(componentContext), WearProjects {

	private val _state = MutableValue(
		WearProjects.State(accountEmail = globalSettingsStore.serverSettings?.email)
	)
	override val state: Value<WearProjects.State> = _state

	// Only touched on the main dispatcher.
	private var watchProjects: List<WatchProject> = emptyList()
	private var syncStatus: SyncStatus = syncCoordinator.status.value
	private var projectsLoaded = false

	override fun onCreate() {
		super.onCreate()
		scope.launch { reload() }
		scope.launch {
			syncCoordinator.status.collect { status ->
				withContext(dispatcherMain) {
					val finished = syncStatus.running && !status.running
					syncStatus = status
					publish()
					// A sync can add, rename, or remove projects.
					if (finished) launch { reload() }
				}
			}
		}
		syncCoordinator.requestAutoSync()
	}

	override fun toggleSubscription(projectName: String) {
		val project = watchProjects.find { it.projectDef.name == projectName } ?: return
		val projectId = project.projectId ?: return
		val subscribe = !project.subscribed

		scope.launch {
			subscriptions.setSubscribed(projectId, subscribe)
			if (subscribe) {
				syncCoordinator.requestSync(SyncTrigger.Manual)
			} else {
				// Thar be dragons: this throws away synced content immediately. Phase 4 must defer it
				// until a sync confirms the server holds every capture taken on the watch, or
				// unsubscribing destroys unsynced writing.
				syncCoordinator.runExclusive { projectsRepository.deleteProjectContent(project.projectDef) }
			}
			reload()
		}
	}

	override fun syncNow() {
		syncCoordinator.requestSync(SyncTrigger.Manual)
	}

	override fun showSyncLog() {
		onShowSyncLog()
	}

	override fun signOut() {
		appScope.launch {
			try {
				signOutUseCase.signOut()
			} catch (e: CancellationException) {
				throw e
			} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
				Napier.e("Sign out failed", e)
			}
		}
	}

	private suspend fun reload() {
		// The scope's job is not a supervisor, so a throw here would take the status collector with it.
		val projects = try {
			listProjects.list()
		} catch (e: CancellationException) {
			throw e
		} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
			Napier.e("Failed to list the watch's projects", e)
			return
		}
		withContext(dispatcherMain) {
			watchProjects = projects
			projectsLoaded = true
			publish()
		}
	}

	private fun publish() {
		val rows = watchProjects.map { project ->
			val progress = syncStatus.projects[project.projectDef.name]
			WearProjects.ProjectRow(
				name = project.projectDef.name,
				canSubscribe = project.projectId != null,
				subscribed = project.subscribed,
				progress = progress?.progress,
				outcome = progress?.outcome,
			)
		}
		val lastResult = syncStatus.lastResult
		_state.update {
			it.copy(
				projects = rows,
				loaded = projectsLoaded,
				syncing = syncStatus.running,
				lastSyncFailed = syncStatus.lastRunFailed || (lastResult != null && !lastResult.allSuccess),
				needsReauth = syncStatus.needsReauth,
			)
		}
	}
}
