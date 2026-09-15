package com.darkrockstudios.apps.hammer.common.data.sync.accountsync

import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.base.http.storyideas.StoryIdea
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.SyncedProjectDefinition
import com.darkrockstudios.apps.hammer.common.data.projectdata.ProjectDataConflictBroker
import com.darkrockstudios.apps.hammer.common.data.projectmetadata.ProjectMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.data.sync.ideassync.IdeaConflict
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.ClientProjectSynchronizer
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncLogMessage
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.syncAccLogI
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.syncLogE
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.syncLogI
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.syncLogW
import com.darkrockstudios.apps.hammer.common.data.temporaryProjectTask
import com.darkrockstudios.apps.hammer.common.util.StrRes
import com.darkrockstudios.apps.hammer.sync_log_begin_account
import com.darkrockstudios.apps.hammer.sync_log_begin_project
import com.darkrockstudios.apps.hammer.sync_log_begin_projects
import com.darkrockstudios.apps.hammer.sync_log_project_conflict
import com.darkrockstudios.apps.hammer.sync_log_project_failed
import com.darkrockstudios.apps.hammer.sync_log_project_unchanged
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import okio.IOException
import org.koin.core.component.KoinComponent
import org.koin.core.parameter.parametersOf

enum class ProjectSyncOutcome {
	Success,

	/** The change probe confirmed nothing to transfer. */
	Unchanged,
	NeedsResolution,
	Failed,

	/** Account sync has not assigned this project a server ID yet. */
	NotOnServer,

	/** Excluded by the caller's project filter. */
	Skipped,
}

data class SyncAccountResult(
	val accountSuccess: Boolean,
	val ideasSuccess: Boolean,
	/** Keyed by project name. */
	val projects: Map<String, ProjectSyncOutcome>,
) {
	val allSuccess: Boolean
		get() = accountSuccess && ideasSuccess && projects.values.none {
			it == ProjectSyncOutcome.Failed || it == ProjectSyncOutcome.NeedsResolution
		}
}

interface SyncAccountListener {
	suspend fun onLog(message: SyncLogMessage)

	/** The full local project list, reported before account sync and again after it. */
	suspend fun onProjectsDiscovered(projects: List<ProjectDef>)

	/** The project is syncing; [progress] is null until the synchronizer reports some. */
	suspend fun onProjectProgress(projectDef: ProjectDef, progress: Float?)

	suspend fun onProjectOutcome(projectDef: ProjectDef, outcome: ProjectSyncOutcome)

	suspend fun onUnauthorized()

	/** Returns the resolved idea, or null to leave the conflict unresolved. */
	suspend fun onIdeaConflict(conflict: IdeaConflict): StoryIdea? = null
}

/**
 * Syncs the account, then every synced project the filter admits, in parallel. Per-project
 * failures are reported as outcomes; failures outside a project propagate to the caller.
 */
class SyncAccountUseCase(
	private val projectsRepository: ProjectsRepository,
	private val accountSynchronizer: ClientAccountSynchronizer,
	private val projectMetadataDatasource: ProjectMetadataDatasource,
	private val strRes: StrRes,
) : KoinComponent {

	suspend fun execute(
		listener: SyncAccountListener,
		projectFilter: (SyncedProjectDefinition) -> Boolean = { true },
	): SyncAccountResult {
		val initialProjects = projectsRepository.getProjects()
		listener.onProjectsDiscovered(initialProjects)

		listener.onLog(syncAccLogI(strRes.get(Res.string.sync_log_begin_account)))

		var ideasSuccess = true
		val accountSuccess = accountSynchronizer.syncProjects(
			onLog = listener::onLog,
			onUnauthorized = listener::onUnauthorized,
			onIdeaConflict = listener::onIdeaConflict,
			onIdeasSyncResult = { ideasSuccess = it },
		)

		yield()

		if (!accountSuccess) {
			initialProjects.forEach { listener.onProjectOutcome(it, ProjectSyncOutcome.Failed) }
			return SyncAccountResult(
				accountSuccess = false,
				ideasSuccess = ideasSuccess,
				projects = initialProjects.associate { it.name to ProjectSyncOutcome.Failed },
			)
		}

		listener.onLog(syncAccLogI(strRes.get(Res.string.sync_log_begin_projects)))

		// Account sync may have created or removed local projects.
		val projects = projectsRepository.getProjects()
		listener.onProjectsDiscovered(projects)

		val outcomes = mutableMapOf<String, ProjectSyncOutcome>()
		val candidates = projects.mapNotNull { projectDef ->
			val outcome = classify(projectDef, projectFilter)
			if (outcome is Candidate.Settled) {
				outcomes[projectDef.name] = outcome.outcome
				listener.onProjectOutcome(projectDef, outcome.outcome)
				null
			} else {
				(outcome as Candidate.ToSync).synced
			}
		}

		val unchangedProjectIds = accountSynchronizer.probeUnchangedProjects(candidates)

		val synced = coroutineScope {
			candidates.map { synced ->
				async {
					val projectDef = synced.projectDef
					val outcome = if (synced.projectId in unchangedProjectIds) {
						listener.onLog(syncLogI(strRes.get(Res.string.sync_log_project_unchanged), projectDef))
						ProjectSyncOutcome.Unchanged
					} else {
						syncProjectSafely(projectDef, listener)
					}
					listener.onProjectOutcome(projectDef, outcome)
					projectDef.name to outcome
				}
			}.awaitAll()
		}
		outcomes.putAll(synced)

		return SyncAccountResult(
			accountSuccess = true,
			ideasSuccess = ideasSuccess,
			projects = outcomes,
		)
	}

	private sealed interface Candidate {
		data class Settled(val outcome: ProjectSyncOutcome) : Candidate
		data class ToSync(val synced: SyncedProjectDefinition) : Candidate
	}

	private fun classify(
		projectDef: ProjectDef,
		projectFilter: (SyncedProjectDefinition) -> Boolean,
	): Candidate {
		// The project can be deleted concurrently.
		val metadata = try {
			projectMetadataDatasource.loadMetadata(projectDef)
		} catch (e: IOException) {
			Napier.w("Failed to load metadata for '${projectDef.name}'", e)
			return Candidate.Settled(ProjectSyncOutcome.Failed)
		}

		val serverProjectId = metadata.info.serverProjectId
		if (serverProjectId == null) {
			Napier.w { "Skipping project sync for '${projectDef.name}' - no server project ID yet" }
			return Candidate.Settled(ProjectSyncOutcome.NotOnServer)
		}

		val synced = SyncedProjectDefinition(projectDef, serverProjectId)
		return if (projectFilter(synced)) {
			Candidate.ToSync(synced)
		} else {
			Candidate.Settled(ProjectSyncOutcome.Skipped)
		}
	}

	private suspend fun syncProjectSafely(
		projectDef: ProjectDef,
		listener: SyncAccountListener,
	): ProjectSyncOutcome {
		return try {
			listener.onProjectProgress(projectDef, null)
			syncProject(projectDef, listener)
		} catch (e: CancellationException) {
			throw e
		} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
			Napier.e("Project sync failed for ${projectDef.name}", e)
			listener.onLog(
				syncLogE(
					strRes.get(Res.string.sync_log_project_failed, e.message ?: "Unknown error"),
					projectDef
				)
			)
			ProjectSyncOutcome.Failed
		}
	}

	private suspend fun syncProject(
		projectDef: ProjectDef,
		listener: SyncAccountListener,
	): ProjectSyncOutcome {
		listener.onLog(syncLogI(strRes.get(Res.string.sync_log_begin_project, projectDef.name), projectDef))

		var success = false
		var conflicted = false

		suspend fun reportConflict() {
			listener.onLog(
				syncLogW(strRes.get(Res.string.sync_log_project_conflict, projectDef.name), projectDef)
			)
			conflicted = true
		}

		try {
			temporaryProjectTask(projectDef) { projScope ->
				val synchronizer: ClientProjectSynchronizer =
					projScope.get { parametersOf(projectDef) }
				val conflictBroker: ProjectDataConflictBroker =
					projScope.get { parametersOf(projectDef) }

				coroutineScope {
					// Bulk account sync has no interactive resolver. A project-data conflict reports
					// to the broker and waits on resolutions forever, leaving the project stuck
					// "Syncing". Watch for it and abort so the project stops instead of hanging.
					val conflictWatcher = launch {
						for (conflict in conflictBroker.conflicts) {
							reportConflict()
							conflictBroker.abort()
						}
					}

					try {
						success = synchronizer.sync(
							onProgress = { progress, message ->
								listener.onProjectProgress(projectDef, progress)
								if (message != null) listener.onLog(message)
							},
							onLog = listener::onLog,
							onConflict = {
								reportConflict()
								throw IllegalStateException("Entity conflict must be handled by Project sync")
							},
							onComplete = {},
							onUnauthorized = listener::onUnauthorized,
						)
					} finally {
						conflictWatcher.cancel()
					}
				}
			}
		} catch (e: CancellationException) {
			throw e
		} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
			// A conflict aborts the entity sync by throwing; that's a resolvable state, not a failure.
			if (!conflicted) throw e
		}

		return when {
			success -> ProjectSyncOutcome.Success
			conflicted -> ProjectSyncOutcome.NeedsResolution
			else -> ProjectSyncOutcome.Failed
		}
	}
}
