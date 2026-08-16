package com.darkrockstudios.apps.hammer.common.data.sync.accountsync

import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.http.ApiProjectDefinition
import com.darkrockstudios.apps.hammer.base.http.BeginProjectsSyncResponse
import com.darkrockstudios.apps.hammer.base.http.ProjectHashItem
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectData
import com.darkrockstudios.apps.hammer.base.http.synchronizer.ProjectContentHasher
import com.darkrockstudios.apps.hammer.base.http.synchronizer.ProjectDataHasher
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.SyncedProjectDefinition
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.isSuccess
import com.darkrockstudios.apps.hammer.common.data.projectdata.loadStoredProjectData
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.data.sync.ideassync.ClientIdeasSynchronizer
import com.darkrockstudios.apps.hammer.common.data.sync.ideassync.IdeaConflictResolver
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.*
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import com.darkrockstudios.apps.hammer.common.server.HttpFailureException
import com.darkrockstudios.apps.hammer.common.server.ServerProjectsApi
import com.darkrockstudios.apps.hammer.common.util.NetworkConnectivity
import com.darkrockstudios.apps.hammer.common.util.StrRes
import io.github.aakira.napier.Napier
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import net.peanuuutz.tomlkt.Toml
import okio.FileSystem
import okio.Path
import kotlin.coroutines.cancellation.CancellationException
import kotlin.uuid.Uuid

class ClientAccountSynchronizer(
	private val fileSystem: FileSystem,
	private val globalSettingsStore: GlobalSettingsStore,
	private val projectsRepository: ProjectsRepository,
	private val serverProjectsApi: ServerProjectsApi,
	private val ideasSynchronizer: ClientIdeasSynchronizer,
	private val networkConnectivity: NetworkConnectivity,
	private val json: Json,
	private val toml: Toml,
	private val strRes: StrRes,
) {
	var initialSync = false

	fun isServerSynchronized(): Boolean {
		return (globalSettingsStore.serverSettings?.userId ?: -1) > -1
	}

	suspend fun shouldAutoSync(): Boolean =
		globalSettingsStore.globalSettings.automaticSyncing &&
			networkConnectivity.hasActiveConnection()

	// Must-not-crash sync boundary; any failure logged and reported as false.
	@Suppress("TooGenericExceptionCaught")
	suspend fun syncProjects(
		onLog: OnSyncLog,
		onUnauthorized: suspend () -> Unit,
		onIdeaConflict: IdeaConflictResolver = { null },
		// The ideas phase's success is reported separately rather than folded into the return
		// value: a transient idea failure must not gate the (much more important) project sync,
		// but the caller still needs it so a failed idea sync doesn't show a success toast.
		onIdeasSyncResult: (Boolean) -> Unit = {},
	): Boolean {
		onLog(syncAccLogI(strRes.get(Res.string.sync_log_account_begin)))

		var syncId: String? = null
		return try {
			val result = serverProjectsApi.beginProjectsSync()
			if (result.isSuccess) {
				onLog(syncAccLogI(strRes.get(Res.string.sync_log_account_server_data_loaded)))

				val serverSyncData = result.getOrThrow()
				syncId = serverSyncData.syncId

				val clientSyncData = loadSyncData()
				val serverKnownIds = serverSyncData.knownProjectIds()

				yield()

				// Deletions go first: a project name is unique per account on the server, so a
				// rename into a name still held by a project queued for deletion is rejected.
				syncDeletedProjects(clientSyncData, serverSyncData, onLog, onUnauthorized)

				yield()

				syncRenamedProjects(clientSyncData, serverSyncData, serverKnownIds, onLog, onUnauthorized)

				yield()

				val updatedServerSyncData = processProjectSyncData(serverSyncData, clientSyncData)

				val localProjects = projectsRepository.getProjects()
				syncCreatedProjects(
					clientSyncData,
					updatedServerSyncData,
					serverKnownIds,
					localProjects,
					onLog,
					onUnauthorized,
				)

				yield()

				// Ideas phase rides the same session. Its outcome is reported to the caller but
				// does not gate project sync — the manuscript is more important than the ideas.
				val ideasSuccess = ideasSynchronizer.syncIdeas(
					syncId = syncId,
					onLog = onLog,
					resolveConflict = onIdeaConflict,
					serverIdeasStateHash = serverSyncData.ideasStateHash,
				)
				onIdeasSyncResult(ideasSuccess)

				yield()

				serverProjectsApi.endProjectsSync(syncId)
				onLog(syncAccLogI(strRes.get(Res.string.sync_log_account_complete)))
				true
			} else {
				onLog(
					syncAccLogE(
						strRes.get(
							Res.string.sync_log_account_failed,
							result.exceptionOrNull() ?: "---"
						)
					)
				)

				if (result.exceptionOrNull().isAuthenticationFailure()) {
					onUnauthorized()
				}

				false
			}
		} catch (e: CancellationException) {
			Napier.i("Projects sync canceled: ${e.message}")

			// End the session even while cancelling, or it leaks server-side and blocks the
			// next begin until it expires.
			syncId?.let {
				withContext(NonCancellable) { serverProjectsApi.endProjectsSync(it) }
			}
			throw e
		} catch (e: Exception) {
			Napier.e("Projects sync failed", e)

			syncId?.let {
				withContext(NonCancellable) { serverProjectsApi.endProjectsSync(it) }
			}

			if (e.isAuthenticationFailure()) {
				onUnauthorized()
			}

			false
		}
	}

	/**
	 * Pre-sync change probe. Reads each project's cached project-wide hash from its journal and asks
	 * the server, in a single request, which of them still match. Returns the [ProjectId]s the caller
	 * can skip syncing this session.
	 *
	 * A project is only probed when it is provably clean: it has a cached hash at the current algorithm
	 * version and no pending local work — neither pending entity work (`dirty`/`newIds`) nor an
	 * unsynced project-data edit. (`deletedIds` is deliberately excluded: it is a persistent tombstone
	 * set, not pending work — matching [SyncJournal.needsSync] — and deletions are already caught by
	 * the project-wide hash itself.) A cached hash coexisting with pending work is an invariant
	 * violation (a mutation that failed to clear the cache) — we log it and fall back to a full sync.
	 *
	 * The whole probe is best-effort: any failure (an unreadable journal, an I/O error, a probe call
	 * that fails) degrades to an empty set so everything syncs the normal way. It must never turn a
	 * recoverable per-project issue into a failure of the surrounding account sync.
	 */
	@Suppress("TooGenericExceptionCaught")
	suspend fun probeUnchangedProjects(projects: List<SyncedProjectDefinition>): Set<ProjectId> {
		return try {
			val items = mutableListOf<ProjectHashItem>()
			for (synced in projects) {
				val syncData = loadProjectSyncData(synced.projectDef) ?: continue
				if (syncData.hashAlgoVersion != ProjectContentHasher.ALGO_VERSION) continue
				val cachedHash = syncData.cachedProjectHash ?: continue

				val entityWorkPending = syncData.dirty.isNotEmpty() || syncData.newIds.isNotEmpty()
				val projectDataDirty = isProjectDataDirty(synced.projectDef)
				if (entityWorkPending || projectDataDirty) {
					val kind = if (projectDataDirty) "project-data" else "journal"
					Napier.e(
						"Cache/journal inconsistency for project '${synced.projectDef.name}': cached hash " +
							"present despite pending $kind work. A write path likely bypassed the " +
							"invalidation hook. Forcing full sync."
					)
					continue
				}

				items += ProjectHashItem(synced.projectId, cachedHash)
			}

			if (items.isEmpty()) return emptySet()

			val result = serverProjectsApi.probeProjectChanges(items)
			if (result.isSuccess) {
				result.getOrThrow().unchangedProjects
			} else {
				Napier.w("Sync probe failed; syncing all projects", result.exceptionOrNull())
				emptySet()
			}
		} catch (e: CancellationException) {
			throw e
		} catch (e: Exception) {
			Napier.w("Sync probe failed; syncing all projects", e)
			emptySet()
		}
	}

	/**
	 * True when the project's local project-data blob differs from the hash the server last confirmed.
	 * Project-data edits don't touch the entity journal, so this is their backstop: it catches a stale
	 * cached hash even if [ProjectDataRepository.updateData]'s invalidation was missed. A never-synced
	 * project (null `lastSyncedHash`) baselines against the default-data hash, which is what the server
	 * holds for it too.
	 */
	private suspend fun isProjectDataDirty(projectDef: ProjectDef): Boolean {
		val stored = loadStoredProjectData(projectDef, fileSystem, toml)
		val currentHash = ProjectDataHasher.hash(stored.data)
		val baseline = stored.lastSyncedHash ?: ProjectDataHasher.hash(ProjectData())
		return currentHash != baseline
	}

	// Reads a project's sync journal off the IO dispatcher without opening a project scope. Any read
	// or parse failure just makes the project ineligible for the probe (it full-syncs) — never throws.
	@Suppress("TooGenericExceptionCaught", "SwallowedException")
	private suspend fun loadProjectSyncData(projectDef: ProjectDef): ProjectSynchronizationData? =
		withContext(Dispatchers.IO) {
			val path = projectDef.path.toOkioPath() / SyncDataDatasource.SYNC_FILE_NAME
			if (!fileSystem.exists(path)) return@withContext null
			try {
				fileSystem.read(path) { json.decodeFromString<ProjectSynchronizationData>(readUtf8()) }
			} catch (e: Exception) {
				Napier.d("Unreadable sync journal for '${projectDef.name}', will full-sync: ${e.message}")
				null
			}
		}

	private fun processProjectSyncData(
		serverSyncData: BeginProjectsSyncResponse,
		clientSyncData: ProjectsSynchronizationData
	): BeginProjectsSyncResponse {
		// Remove client deleted projects the list of projects to sync
		var updatedServerSyncData = serverSyncData.copy(
			projects = serverSyncData.projects.filter { serverProj ->
				clientSyncData.projectsToDelete.none { clientProj ->
					clientProj.id == serverProj.uuid.id
				}
			}.toSet()
		)

		// Replace client renamed projects in the list of projects to sync
		updatedServerSyncData = updatedServerSyncData.copy(
			projects = updatedServerSyncData.projects.map { serverProj ->
				val renamed = clientSyncData.projectsToRename
					.find { (clientProjId, _) -> clientProjId == serverProj.uuid }

				if (renamed != null) {
					serverProj.copy(name = renamed.newName)
				} else {
					serverProj
				}
			}.toSet()
		)

		return updatedServerSyncData
	}

	private suspend fun syncRenamedProjects(
		clientSyncData: ProjectsSynchronizationData,
		serverSyncData: BeginProjectsSyncResponse,
		serverKnownIds: Set<ProjectId>,
		onLog: OnSyncLog,
		onUnauthorized: suspend () -> Unit = {},
	) {
		// Rename projects on the server
		clientSyncData.projectsToRename.forEach { (projectId, newName) ->
			// The server can't rename an id it never issued, and would fail this every session
			// forever. Recreation covers the rename anyway: it creates under the local name,
			// which a local rename has already updated. A tombstoned id is just as unrenamable,
			// and its local project has already been deleted by syncDeletedProjects.
			if (projectId !in serverKnownIds || projectId in serverSyncData.deletedProjects) {
				dropQueuedRename(projectId)
				return@forEach
			}

			val result = serverProjectsApi.renameProject(projectId, serverSyncData.syncId, newName)
			if (result.isSuccess) {
				onLog(
					syncAccLogI(
						strRes.get(
							Res.string.sync_log_account_project_rename_server_success,
							projectId.id
						)
					)
				)
				dropQueuedRename(projectId)
			} else {
				val exception = result.exceptionOrNull()
				Napier.e("Failed to rename project: $projectId", exception)

				onLog(
					syncAccLogE(
						strRes.get(
							Res.string.sync_log_account_project_rename_server_failure,
							projectId
						)
					)
				)

				if (exception.isAuthenticationFailure()) {
					onUnauthorized()
				}
			}
		}
	}

	private fun dropQueuedRename(projectId: ProjectId) {
		updateSyncData { syncData ->
			syncData.copy(
				projectsToRename = syncData.projectsToRename
					.filterNot { it.projectId == projectId }.toSet(),
			)
		}
	}

	private suspend fun syncDeletedProjects(
		clientSyncData: ProjectsSynchronizationData,
		serverSyncData: BeginProjectsSyncResponse,
		onLog: OnSyncLog,
		onUnauthorized: suspend () -> Unit = {},
	) {
		deleteServerProjects(clientSyncData, serverSyncData, onLog, onUnauthorized)
		deleteLocalProjects(clientSyncData, serverSyncData, onLog)
	}

	/**
	 * Delete local projects which the server has deleted
	 */
	private suspend fun deleteLocalProjects(
		clientSyncData: ProjectsSynchronizationData,
		serverSyncData: BeginProjectsSyncResponse,
		onLog: OnSyncLog
	) {
		val newlyDeletedProjects = serverSyncData.deletedProjects.filter { project ->
			clientSyncData.deletedProjects.contains(project).not()
		}

		newlyDeletedProjects.forEach { projectId ->
			val projectDef = projectsRepository.findProject(projectId)
			if (projectDef != null) {
				onLog(
					syncAccLogI(
						strRes.get(
							Res.string.sync_log_account_project_delete_client,
							projectDef.name
						)
					)
				)

				projectsRepository.deleteProject(projectDef)
			}
		}
	}

	/**
	 * Delete projects on the server which this client deleted locally
	 */
	private suspend fun deleteServerProjects(
		clientSyncData: ProjectsSynchronizationData,
		serverSyncData: BeginProjectsSyncResponse,
		onLog: OnSyncLog,
		onUnauthorized: suspend () -> Unit = {},
	) {
		clientSyncData.projectsToDelete.forEach { projectId ->
			val result = serverProjectsApi.deleteProject(projectId, serverSyncData.syncId)
			if (result.isSuccess) {
				onLog(
					syncAccLogI(
						strRes.get(
							Res.string.sync_log_account_project_delete_server_success,
							projectId.id
						)
					)
				)
				updateSyncData { syncData ->
					syncData.copy(
						projectsToDelete = syncData.projectsToDelete - projectId,
						deletedProjects = syncData.deletedProjects + projectId,
					)
				}
			} else {
				val exception = result.exceptionOrNull()
				Napier.e("Failed to delete project: $projectId", exception)

				onLog(
					syncAccLogE(
						strRes.get(
							Res.string.sync_log_account_project_delete_server_failure,
							projectId
						)
					)
				)

				if (exception.isAuthenticationFailure()) {
					onUnauthorized()
				}
			}
		}
	}

	private suspend fun syncCreatedProjects(
		clientSyncData: ProjectsSynchronizationData,
		serverSyncData: BeginProjectsSyncResponse,
		serverKnownIds: Set<ProjectId>,
		localProjects: List<ProjectDef>,
		onLog: OnSyncLog,
		onUnauthorized: suspend () -> Unit = {},
	) {
		val localProjectsWithIds = localProjects.map {
			val projectId = projectsRepository.getProjectId(it)
			it to projectId
		}

		val serverProjects = serverSyncData.projects
		val newServerProjects = serverProjects.filter { serverProject ->
			localProjectsWithIds.none { (_, uuid) ->
				uuid == serverProject.uuid
			}
		}

		renameLocalProjectsFromServerChanges(
			serverProjects,
			localProjectsWithIds,
			clientSyncData,
			onLog
		)

		// Claim server projects by name before uploading anything. The other order uploads a
		// same-named local project as a second server project and only then overwrites its id
		// with the name match, stranding an orphan duplicate on the server.
		val liveServerIds = serverProjects.mapTo(mutableSetOf()) { it.uuid }
		val claimedNames = createLocalProjectsFromServer(newServerProjects, liveServerIds, onLog)

		createProjectsOnServer(
			localProjectsWithIds,
			claimedNames,
			clientSyncData,
			serverSyncData,
			serverKnownIds,
			onLog,
			onUnauthorized,
		)
	}

	/**
	 * Create local projects from server. Returns the local names now backed by a server project,
	 * whether adopted or freshly created, so [createProjectsOnServer] does not re-upload them.
	 *
	 * A name match only claims a local project that is not already bound to one of
	 * [liveServerIds]: re-pointing it would abandon the server project holding its content, and
	 * two server names can sanitize to a single local name. Tombstoned ids are absent from
	 * [liveServerIds], so a project whose server copy was deleted elsewhere can still be claimed.
	 */
	private suspend fun createLocalProjectsFromServer(
		newServerProjects: List<ApiProjectDefinition>,
		liveServerIds: Set<ProjectId>,
		onLog: OnSyncLog
	): Set<String> {
		val claimedNames = mutableSetOf<String>()
		newServerProjects.forEach { serverProject ->
			val localName = ProjectsRepository.toLocalSafeName(serverProject.name)
			val existingProject = projectsRepository.findProject(localName)
			if (existingProject != null) {
				val existingId = projectsRepository.getProjectId(existingProject)
				if (existingId != null && existingId in liveServerIds) {
					onLog(
						syncAccLogW(
							strRes.get(
								Res.string.sync_log_account_project_create_client_name_taken,
								serverProject.name
							)
						)
					)
					return@forEach
				}

				projectsRepository.setProjectId(existingProject, serverProject.uuid)
				claimedNames += existingProject.name
				onLog(
					syncAccLogI(
						strRes.get(
							Res.string.sync_log_account_project_create_client_local,
							localName
						)
					)
				)
			} else {
				val createResult = projectsRepository.createProject(localName, seedDefaultLanguage = false)
				if (isSuccess(createResult)) {
					val projectDef = createResult.data
					projectsRepository.setProjectId(projectDef, serverProject.uuid)
					claimedNames += projectDef.name
					onLog(
						syncAccLogI(
							strRes.get(
								Res.string.sync_log_account_project_create_client,
								localName
							)
						)
					)
				} else {
					onLog(
						syncAccLogE(
							strRes.get(
								Res.string.sync_log_account_project_create_local_failure,
								serverProject.name,
							)
						)
					)
				}
			}
		}
		return claimedNames
	}

	/**
	 * Create projects on the server which this client has created locally. A cached [ProjectId]
	 * absent from [serverKnownIds] is dead (the client last synced against a different or
	 * since-reset server): recreate it, or per-project sync gets an id it can only 410 on.
	 *
	 * [claimedNames] are local projects that just adopted a server project by name; uploading them
	 * would duplicate that project on the server.
	 */
	private suspend fun createProjectsOnServer(
		localProjectsWithIds: List<Pair<ProjectDef, ProjectId?>>,
		claimedNames: Set<String>,
		clientSyncData: ProjectsSynchronizationData,
		serverSyncData: BeginProjectsSyncResponse,
		serverKnownIds: Set<ProjectId>,
		onLog: OnSyncLog,
		onUnauthorized: suspend () -> Unit = {},
	) {
		val localOnly = localProjectsWithIds.filter { (def, uuid) ->
			(uuid == null || uuid !in serverKnownIds) && def.name !in claimedNames
		}.map { it.first.name }

		// A queued name with no local project was deleted before it ever reached the server.
		// Creating it would push an empty project to every device, and resolving its definition
		// would point at a directory that is gone. A queued name that just adopted a server
		// project is equally dead. Drop both rather than acting on them.
		val localNames = localProjectsWithIds.mapTo(mutableSetOf()) { it.first.name }
		val withdrawnCreates = clientSyncData.projectsToCreate
			.filterTo(mutableSetOf()) { it !in localNames || it in claimedNames }
		if (withdrawnCreates.isNotEmpty()) {
			updateSyncData { syncData ->
				syncData.copy(projectsToCreate = syncData.projectsToCreate - withdrawnCreates)
			}
		}

		val newLocalProjects = (clientSyncData.projectsToCreate - withdrawnCreates) + localOnly
		// Create projects on the server
		newLocalProjects.forEach { projectName ->
			val result = serverProjectsApi.createProject(projectName, serverSyncData.syncId)
			if (result.isSuccess) {
				// Save the newly provisioned project id
				val response = result.getOrThrow()
				val projectDef = projectsRepository.getProjectDefinition(projectName)
				val idSaved = try {
					projectsRepository.setProjectId(projectDef, response.projectId)
					true
				} catch (e: Exception) {
					Napier.e("Failed to save project id for $projectName", e)
					onLog(
						syncAccLogE(
							strRes.get(
								Res.string.sync_log_account_project_id_save_failure,
								projectName
							)
						)
					)
					false
				}

				if (idSaved) {
					onLog(
						syncAccLogI(
							strRes.get(
								Res.string.sync_log_account_project_create_server_success,
								projectName
							)
						)
					)
					updateSyncData { syncData ->
						syncData.copy(
							projectsToCreate = syncData.projectsToCreate - projectName,
						)
					}
				}
			} else {
				onLog(
					syncAccLogE(
						strRes.get(
							Res.string.sync_log_account_project_create_server_failure,
							projectName
						)
					)
				)

				val exception = result.exceptionOrNull()
				if (exception.isAuthenticationFailure()) {
					onUnauthorized()
				}
			}
		}
	}

	/**
	 * Rename projects on this client which the server has renamed
	 */
	private suspend fun renameLocalProjectsFromServerChanges(
		serverProjects: Set<ApiProjectDefinition>,
		localProjectsWithIds: List<Pair<ProjectDef, ProjectId?>>,
		clientSyncData: ProjectsSynchronizationData,
		onLog: OnSyncLog
	) {
		val commonProjectsNotLocallyRenamed = serverProjects.mapNotNull { serverProject ->
			localProjectsWithIds.find { it.second == serverProject.uuid }?.let { localProject ->
				ProjectPair(serverProject, localProject.first)
			}
		}
			.filterNot { clientSyncData.projectsToDelete.contains(it.serverProject.uuid) }
			.filterNot {
				clientSyncData.projectsToRename
					.find { renamed -> renamed.projectId == it.serverProject.uuid } != null
			}

		// Handle projects that have been renamed on the server, but not on this client
		commonProjectsNotLocallyRenamed.forEach { (serverProject, localProject) ->
			val targetName = ProjectsRepository.toLocalSafeName(serverProject.name)
			if (targetName != localProject.name) {
				val result = projectsRepository.renameProject(localProject, targetName)
				if (isSuccess(result)) {
					onLog(
						syncAccLogI(
							strRes.get(
								Res.string.sync_log_account_project_rename_client_from_server_success,
								targetName
							)
						)
					)
				} else {
					onLog(
						syncAccLogE(
							strRes.get(
								Res.string.sync_log_account_project_rename_client_from_server_failure,
								localProject.name
							)
						)
					)
				}
			}
		}
	}

	fun deleteProject(project: SyncedProjectDefinition) {
		updateSyncData { syncData ->
			syncData.copy(
				projectsToDelete = syncData.projectsToDelete + project.projectId,
				projectsToCreate = syncData.projectsToCreate - project.projectDef.name,
				projectsToRename = syncData.projectsToRename
					.filterNot { it.projectId == project.projectId }
					.toSet(),
			)
		}
	}

	fun renameProject(projectId: ProjectId, newName: String) {
		val renamed = RenamedProject(projectId, newName)
		updateSyncData { syncData ->
			// Remove any old renames of this project and add this new one
			val updated = syncData.projectsToRename
				.filterNot { it.projectId == projectId } + renamed

			syncData.copy(
				projectsToRename = updated.toSet(),
			)
		}
	}

	fun createProject(projectName: String) {
		updateSyncData { syncData ->
			syncData.copy(
				projectsToCreate = syncData.projectsToCreate + projectName,
			)
		}
	}

	/**
	 * A project deleted before it ever synced has no id to tombstone, only a queued creation to
	 * withdraw. Left queued, the next sync would create it on the server and push it back to
	 * every device.
	 */
	fun deleteUnsyncedProject(projectName: String) {
		updateSyncData { syncData ->
			syncData.copy(
				projectsToCreate = syncData.projectsToCreate - projectName,
			)
		}
	}

	private fun getSyncDataPath(): Path =
		projectsRepository.getProjectsDirectory().toOkioPath() / SYNC_FILE_NAME

	// Corrupt sync data recovers by recreating it; not an error to surface.
	@Suppress("SwallowedException")
	private fun loadSyncData(): ProjectsSynchronizationData {
		val path = getSyncDataPath()
		val syncData = if (fileSystem.exists(path)) {
			fileSystem.read(path) {
				val syncDataJson = readUtf8()
				try {
					json.decodeFromString(syncDataJson)
				} catch (e: SerializationException) {
					createAndSaveSyncData()
				}
			}
		} else {
			createAndSaveSyncData()
		}

		// Handling migration which replaced project names with UUIDs
		val projectsToDelete = syncData.projectsToDelete.filter {
			try {
				Uuid.parse(it.id)
				true
			} catch (e: IllegalArgumentException) {
				Napier.w("Invalid UUID for deleted project: $it", e)
				false
			}
		}
		val deletedProjects = syncData.deletedProjects.filter {
			try {
				Uuid.parse(it.id)
				true
			} catch (e: IllegalArgumentException) {
				Napier.w("Invalid UUID for deleted project: $it")
				false
			}
		}

		return syncData.copy(
			projectsToDelete = projectsToDelete.toSet(),
			deletedProjects = deletedProjects.toSet(),
		)
	}

	private fun createAndSaveSyncData(): ProjectsSynchronizationData {
		val newData = ProjectsSynchronizationData(
			deletedProjects = emptySet(),
			projectsToDelete = emptySet(),
			projectsToRename = emptySet(),
			projectsToCreate = emptySet(),
		)
		saveSyncData(newData)

		return newData
	}

	private fun saveSyncData(data: ProjectsSynchronizationData) {
		val path = getSyncDataPath()
		fileSystem.write(path) {
			val syncDataJson = json.encodeToString(data)
			writeUtf8(syncDataJson)
		}
	}

	private fun updateSyncData(action: (ProjectsSynchronizationData) -> ProjectsSynchronizationData) {
		val data = loadSyncData()
		val update = action(data)
		saveSyncData(update)
	}

	companion object {
		private const val SYNC_FILE_NAME = "sync.json"
	}
}

private data class ProjectPair(
	val serverProject: ApiProjectDefinition,
	val localProject: ProjectDef,
)

private fun Throwable?.isAuthenticationFailure(): Boolean =
	(this is HttpFailureException && statusCode == HttpStatusCode.Unauthorized)

/**
 * Every project id the server accounted for, live or tombstoned. Must be read from the raw
 * begin_sync response: `processProjectSyncData` drops projects this client has queued for
 * deletion, and a project missing for that reason is still known to the server, not dead.
 */
private fun BeginProjectsSyncResponse.knownProjectIds(): Set<ProjectId> =
	projects.mapTo(mutableSetOf()) { it.uuid } + deletedProjects