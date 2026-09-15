package com.darkrockstudios.apps.hammer.wear.components.projects

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.update
import com.darkrockstudios.apps.hammer.common.components.ComponentBase
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.wear.data.ListWatchProjectsUseCase
import com.darkrockstudios.apps.hammer.wear.data.SignOutUseCase
import com.darkrockstudios.apps.hammer.wear.data.SubscribedProjectsRepository
import com.darkrockstudios.apps.hammer.wear.data.WatchProject
import com.darkrockstudios.apps.hammer.wear.sync.SyncCoordinator
import com.darkrockstudios.apps.hammer.wear.sync.SyncStatus
import com.darkrockstudios.apps.hammer.wear.sync.SyncTrigger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WearProjectsComponent(
	componentContext: ComponentContext,
	globalSettingsStore: GlobalSettingsStore,
	private val listProjects: ListWatchProjectsUseCase,
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
			reload()
			if (subscribe) syncCoordinator.requestSync(SyncTrigger.Manual)
		}
	}

	override fun syncNow() {
		syncCoordinator.requestSync(SyncTrigger.Manual)
	}

	override fun showSyncLog() {
		onShowSyncLog()
	}

	override fun signOut() {
		appScope.launch { signOutUseCase.signOut() }
	}

	private suspend fun reload() {
		val projects = listProjects.list()
		withContext(dispatcherMain) {
			watchProjects = projects
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
				loaded = true,
				syncing = syncStatus.running,
				lastSyncFailed = syncStatus.lastRunFailed || (lastResult != null && !lastResult.allSuccess),
				needsReauth = syncStatus.needsReauth,
			)
		}
	}
}
