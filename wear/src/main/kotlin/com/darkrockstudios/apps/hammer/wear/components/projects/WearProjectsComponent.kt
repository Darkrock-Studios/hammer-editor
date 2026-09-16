package com.darkrockstudios.apps.hammer.wear.components.projects

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.update
import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.common.components.ComponentBase
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.wear.data.ListWatchProjectsUseCase
import com.darkrockstudios.apps.hammer.wear.data.SignOutUseCase
import com.darkrockstudios.apps.hammer.wear.data.SubscribedProjectsRepository
import com.darkrockstudios.apps.hammer.wear.data.UnsyncedContentUseCase
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
	private val unsyncedContent: UnsyncedContentUseCase,
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
	private var unsubscribing: Set<String> = emptySet()
	private var signOutWarning: WearProjects.SignOutWarning? = null
	private var unsyncedKept: String? = null

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

		if (!project.subscribed) {
			scope.launch {
				subscriptions.setSubscribed(projectId, true)
				syncCoordinator.requestSync(SyncTrigger.Manual)
				reload()
			}
			return
		}

		if (projectName in unsubscribing) return
		unsubscribing = unsubscribing + projectName
		unsyncedKept = null
		publish()
		// Outlives the screen: abandoning a half-done unsubscribe would strand the subscription.
		appScope.launch { unsubscribe(project, projectId) }
	}

	override fun syncNow() {
		syncCoordinator.requestSync(SyncTrigger.Manual)
	}

	override fun showSyncLog() {
		onShowSyncLog()
	}

	override fun signOut() {
		scope.launch {
			val pending = try {
				unsyncedContent.pending()
			} catch (e: CancellationException) {
				throw e
			} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
				Napier.e("Failed to count unsynced captures before signing out", e)
				null
			}

			withContext(dispatcherMain) {
				if (pending != null && pending.isEmpty) {
					runSignOut()
				} else {
					signOutWarning = WearProjects.SignOutWarning(items = pending?.total ?: 0)
					publish()
				}
			}
		}
	}

	override fun confirmSignOut() {
		signOutWarning = null
		publish()
		runSignOut()
	}

	override fun cancelSignOut() {
		signOutWarning = null
		publish()
	}

	override fun dismissUnsyncedNotice() {
		unsyncedKept = null
		publish()
	}

	/**
	 * Uploads before dropping the project's content, so writing captured on the watch is never
	 * destroyed by unsubscribing. If anything is still unsynced the project stays subscribed as
	 * well as intact, because an unsubscribed project is filtered out of every later sync and its
	 * writing would never get another chance to upload.
	 */
	private suspend fun unsubscribe(project: WatchProject, projectId: ProjectId) {
		var kept = true
		try {
			// While the project is still subscribed, so the sync filter includes it.
			if (pendingIn(project) > 0) syncCoordinator.sync(SyncTrigger.Manual)

			kept = !syncCoordinator.runExclusive {
				val synced = pendingIn(project) == 0
				if (synced) {
					subscriptions.setSubscribed(projectId, false)
					projectsRepository.deleteProjectContent(project.projectDef)
				}
				synced
			}
		} catch (e: CancellationException) {
			throw e
		} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
			Napier.e("Failed to unsubscribe from ${project.projectDef.name}", e)
		} finally {
			withContext(dispatcherMain) {
				unsubscribing = unsubscribing - project.projectDef.name
				unsyncedKept = project.projectDef.name.takeIf { kept }
			}
			reload()
		}
	}

	/** An unreadable count counts as pending, so a failure can never authorise a delete. */
	private suspend fun pendingIn(project: WatchProject): Int = try {
		unsyncedContent.pendingIn(project.projectDef)
	} catch (e: CancellationException) {
		throw e
	} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
		Napier.e("Failed to count unsynced writing in ${project.projectDef.name}", e)
		1
	}

	private fun runSignOut() {
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
			val name = project.projectDef.name
			WearProjects.ProjectRow(
				name = name,
				canSubscribe = project.projectId != null,
				subscribed = project.subscribed,
				unsubscribing = name in unsubscribing,
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
				signOutWarning = signOutWarning,
				unsyncedKept = unsyncedKept,
			)
		}
	}
}
