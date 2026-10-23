package com.darkrockstudios.apps.hammer.common.data.sync.accountsync

import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.base.GroupId
import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.http.ApiGroupDefinition
import com.darkrockstudios.apps.hammer.base.http.ApiProjectDefinition
import com.darkrockstudios.apps.hammer.base.http.BeginProjectsSyncResponse
import com.darkrockstudios.apps.hammer.base.http.ProjectHashItem
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectData
import com.darkrockstudios.apps.hammer.base.http.synchronizer.ProjectContentHasher
import com.darkrockstudios.apps.hammer.base.http.synchronizer.ProjectDataHasher
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.isSuccess
import com.darkrockstudios.apps.hammer.common.data.migrator.DataMigrator
import com.darkrockstudios.apps.hammer.common.data.projectdata.loadStoredProjectData
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectInUseException
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectTree
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.groups.GroupSyncPhase
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
import kotlinx.serialization.json.Json
import net.peanuuutz.tomlkt.Toml
import okio.FileSystem
import okio.IOException
import kotlin.coroutines.cancellation.CancellationException

class ClientAccountSynchronizer(
	private val fileSystem: FileSystem,
	private val globalSettingsStore: GlobalSettingsStore,
	private val projectsRepository: ProjectsRepository,
	private val serverProjectsApi: ServerProjectsApi,
	private val accountSyncDatasource: AccountSyncDatasource,
	private val groupSyncPhase: GroupSyncPhase,
	private val projectAttributeSyncPhase: ProjectAttributeSyncPhase,
	private val projectCreateResolver: ProjectCreateResolver,
	private val ideasSynchronizer: ClientIdeasSynchronizer,
	private val dataMigrator: DataMigrator,
	private val networkConnectivity: NetworkConnectivity,
	private val json: Json,
	private val toml: Toml,
	private val strRes: StrRes,
) {
	var initialSync = false

	private val placer = LocalProjectPlacer(projectsRepository, strRes)

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

				val clientSyncData = accountSyncDatasource.load()
				val serverKnownIds = serverSyncData.knownProjectIds()
				val ledger = BaselineLedger()

				yield()

				// Deletions go first: a project name is unique per account on the server, so a
				// rename into a name still held by a project queued for deletion is rejected.
				syncDeletedProjects(clientSyncData, serverSyncData, serverKnownIds, ledger, onLog, onUnauthorized)

				yield()

				// Groups before projects, so a project can be placed in a group created this session.
				val pendingGroupDeletes = groupSyncPhase.reconcile(
					syncId = serverSyncData.syncId,
					serverGroups = serverSyncData.groups,
					baselines = clientSyncData.groupBaselines,
					ledger = ledger,
					onLog = onLog,
					onUnauthorized = onUnauthorized,
				)

				yield()

				val liveServerProjects = serverSyncData.projects
					.filterNot { it.uuid in clientSyncData.projectsToDelete }
				val legacySeeds = seedFromLegacyRenames(
					clientSyncData.projectsToRename,
					liveServerProjects,
					clientSyncData.projectBaselines,
				)
				ledger.seedProjects(legacySeeds)
				projectAttributeSyncPhase.reconcile(
					syncId = serverSyncData.syncId,
					serverProjects = liveServerProjects,
					serverGroups = serverSyncData.groups,
					baselines = clientSyncData.projectBaselines + legacySeeds,
					excluded = clientSyncData.projectsToDelete,
					ledger = ledger,
					onLog = onLog,
					onUnauthorized = onUnauthorized,
				)

				yield()

				syncCreatedProjects(
					serverSyncData,
					liveServerProjects,
					serverKnownIds,
					ledger,
					onLog,
					onUnauthorized,
				)

				yield()

				// After the project phases, so the groups are already empty on both sides.
				groupSyncPhase.applyDeletions(serverSyncData.syncId, pendingGroupDeletes, ledger, onLog, onUnauthorized)

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

				// The legacy rename queue is cleared only here, once its seeds are safely stored.
				accountSyncDatasource.update { ledger.applyTo(it).copy(projectsToRename = emptySet()) }

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
	suspend fun probeUnchangedProjects(projects: List<ProjectDef>): Set<ProjectId> {
		return try {
			val items = mutableListOf<ProjectHashItem>()
			for (projectDef in projects) {
				val syncData = loadProjectSyncData(projectDef) ?: continue
				if (syncData.hashAlgoVersion != ProjectContentHasher.ALGO_VERSION) continue
				val cachedHash = syncData.cachedProjectHash ?: continue
				// A lite sync can cache a hash without running the restore check; it must still run.
				if (syncData.verifyAllEntities) continue

				val entityWorkPending = syncData.dirty.isNotEmpty() || syncData.newIds.isNotEmpty()
				val projectDataDirty = isProjectDataDirty(projectDef)
				if (entityWorkPending || projectDataDirty) {
					val kind = if (projectDataDirty) "project-data" else "journal"
					Napier.e(
						"Cache/journal inconsistency for project '${projectDef.name}': cached hash " +
							"present despite pending $kind work. A write path likely bypassed the " +
							"invalidation hook. Forcing full sync."
					)
					continue
				}

				items += ProjectHashItem(projectDef.id, cachedHash)
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
			val path = projectDef.path.toOkioPath() / SyncJournalDatasource.SYNC_FILE_NAME
			if (!fileSystem.exists(path)) return@withContext null
			try {
				fileSystem.read(path) { json.decodeFromString<ProjectSynchronizationData>(readUtf8()) }
			} catch (e: Exception) {
				Napier.d("Unreadable sync journal for '${projectDef.name}', will full-sync: ${e.message}")
				null
			}
		}

	private suspend fun syncDeletedProjects(
		clientSyncData: ProjectsSynchronizationData,
		serverSyncData: BeginProjectsSyncResponse,
		serverKnownIds: Set<ProjectId>,
		ledger: BaselineLedger,
		onLog: OnSyncLog,
		onUnauthorized: suspend () -> Unit = {},
	) {
		deleteServerProjects(clientSyncData, serverSyncData, serverKnownIds, ledger, onLog, onUnauthorized)
		deleteLocalProjects(clientSyncData, serverSyncData, ledger, onLog)
	}

	/**
	 * Delete local projects which the server has deleted
	 */
	private suspend fun deleteLocalProjects(
		clientSyncData: ProjectsSynchronizationData,
		serverSyncData: BeginProjectsSyncResponse,
		ledger: BaselineLedger,
		onLog: OnSyncLog
	) {
		serverSyncData.deletedProjects.forEach(ledger::retireProject)
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
	 * Delete projects on the server which this client deleted locally. An id the server never knew
	 * belongs to a project deleted before it ever synced: there is nothing to delete, and a
	 * tombstone for it would only refuse a later create of the same id.
	 */
	private suspend fun deleteServerProjects(
		clientSyncData: ProjectsSynchronizationData,
		serverSyncData: BeginProjectsSyncResponse,
		serverKnownIds: Set<ProjectId>,
		ledger: BaselineLedger,
		onLog: OnSyncLog,
		onUnauthorized: suspend () -> Unit = {},
	) {
		clientSyncData.projectsToDelete.forEach { projectId ->
			ledger.retireProject(projectId)
			if (projectId !in serverKnownIds) {
				accountSyncDatasource.update { syncData ->
					syncData.copy(projectsToDelete = syncData.projectsToDelete - projectId)
				}
				return@forEach
			}

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
				accountSyncDatasource.update { syncData ->
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
		serverSyncData: BeginProjectsSyncResponse,
		liveServerProjects: List<ApiProjectDefinition>,
		serverKnownIds: Set<ProjectId>,
		ledger: BaselineLedger,
		onLog: OnSyncLog,
		onUnauthorized: suspend () -> Unit = {},
	) {
		val tree = projectsRepository.getProjectTree()
		val newServerProjects = liveServerProjects.filter { serverProject ->
			tree.projects.none { it.id == serverProject.uuid }
		}

		// Claim server projects by name before uploading anything. The other order uploads a
		// same-named local project as a second server project and only then overwrites its id
		// with the name match, stranding an orphan duplicate on the server.
		val liveServerIds = liveServerProjects.mapTo(mutableSetOf()) { it.uuid }
		val claimedNames = createLocalProjectsFromServer(
			newServerProjects,
			liveServerIds,
			tree,
			serverSyncData.groups.associateBy { it.uuid },
			ledger,
			onLog,
		)

		createProjectsOnServer(
			tree,
			claimedNames,
			serverSyncData,
			serverKnownIds,
			ledger,
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
	 *
	 * Each project goes in its server group, or the nearest ancestor of it this device has. Its
	 * baseline is recorded only when it lands in the server's group itself.
	 */
	private suspend fun createLocalProjectsFromServer(
		newServerProjects: List<ApiProjectDefinition>,
		liveServerIds: Set<ProjectId>,
		tree: ProjectTree,
		serverGroups: Map<GroupId, ApiGroupDefinition>,
		ledger: BaselineLedger,
		onLog: OnSyncLog
	): Set<String> {
		val claimedNames = mutableSetOf<String>()
		// First holder wins, as a scan of the directory would find it.
		val localByName = tree.projects.asReversed().associateByTo(mutableMapOf()) { it.name }
		newServerProjects.forEach { serverProject ->
			val localName = ProjectsRepository.toLocalSafeName(serverProject.name)
			val target = tree.nearestLocalGroup(serverProject.groupId, serverGroups)
			val placed = target?.id == serverProject.groupId
			val baseline = ProjectBaseline(serverProject.name, serverProject.groupId)
			val existingProject = localByName[localName]
			if (existingProject != null) {
				if (existingProject.id in liveServerIds) {
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

				// Claimed even when skipped, so createProjectsOnServer does not upload a duplicate.
				claimedNames += existingProject.name
				val adopted = try {
					projectsRepository.setProjectId(existingProject, serverProject.uuid)
				} catch (_: ProjectInUseException) {
					placer.logInUse(existingProject, onLog)
					return@forEach
				}
				localByName[adopted.name] = adopted
				onLog(
					syncAccLogI(
						strRes.get(
							Res.string.sync_log_account_project_create_client,
							localName
						)
					)
				)
				val currentGroup = tree.placement[existingProject.id]
				if (placer.place(adopted, currentGroup, target, serverProject.groupId, onLog)) {
					ledger.recordProject(serverProject.uuid, baseline)
				}
			} else {
				when (val folder = adoptUnmarkedFolder(localName, serverProject.uuid, onLog)) {
					UnmarkedFolder.Failed -> claimedNames += localName
					is UnmarkedFolder.Adopted -> {
						claimedNames += localName
						localByName[localName] = folder.projectDef
						if (placer.place(folder.projectDef, null, target, serverProject.groupId, onLog)) {
							ledger.recordProject(serverProject.uuid, baseline)
						}
					}

					UnmarkedFolder.None -> {
						val createResult = projectsRepository.createProject(
							localName,
							seedDefaultLanguage = false,
							projectId = serverProject.uuid,
							group = target,
						)
						if (isSuccess(createResult)) {
							val projectDef = createResult.data
							claimedNames += projectDef.name
							localByName[projectDef.name] = projectDef
							if (placed) ledger.recordProject(serverProject.uuid, baseline)
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
			}
		}
		return claimedNames
	}

	/**
	 * Adopts the directory named [localName] under [serverId] if it has no project.toml, treating
	 * it like a restored backup. An I/O failure is logged and reported as [UnmarkedFolder.Failed]:
	 * creating the project there would fail on the same directory, and one project must not abort
	 * the rest of the account sync.
	 *
	 * A failed migration is logged rather than failing the sync: startup retries it, but does not
	 * reset sync state again, so a pre-4 folder migrated only then keeps its stale `lastSyncedHash`
	 * (its entities are still conflict-checked).
	 */
	@Suppress("TooGenericExceptionCaught")
	private suspend fun adoptUnmarkedFolder(localName: String, serverId: ProjectId, onLog: OnSyncLog): UnmarkedFolder {
		val adopted = try {
			projectsRepository.adoptUnmarkedDirectory(localName, serverId, serverKnowsProject = true)
		} catch (e: IOException) {
			Napier.e("Failed to adopt the folder of '$localName'", e)
			onLog(syncAccLogE(strRes.get(Res.string.sync_log_account_project_create_local_failure, localName)))
			return UnmarkedFolder.Failed
		} ?: return UnmarkedFolder.None

		try {
			dataMigrator.migrateProject(adopted)
		} catch (e: CancellationException) {
			throw e
		} catch (e: Exception) {
			Napier.e("Adopted '${adopted.name}' but failed to migrate it", e)
		}
		// Migration3_4 can move a pre-4 settings file, stale sync baseline included, into place.
		// The journal was already flagged when the folder was adopted, so a failure here is logged.
		try {
			projectsRepository.resetSyncStateAfterRestore(adopted, serverKnowsProject = true)
		} catch (e: IOException) {
			Napier.e("Adopted '${adopted.name}' but failed to reset its sync state", e)
		}

		onLog(syncAccLogI(strRes.get(Res.string.sync_log_account_project_adopt_folder, localName)))
		return UnmarkedFolder.Adopted(adopted)
	}

	private sealed interface UnmarkedFolder {
		data object None : UnmarkedFolder
		data object Failed : UnmarkedFolder
		data class Adopted(val projectDef: ProjectDef) : UnmarkedFolder
	}

	/**
	 * Create projects on the server which this client has created locally: every local id absent
	 * from [serverKnownIds]. That covers a project never synced as well as one last synced against
	 * a different or since-reset server, which per-project sync could only 410 on.
	 *
	 * [claimedNames] are local projects that just adopted a server project by name; uploading them
	 * would duplicate that project on the server. A project is created in its group only when the
	 * server holds that group; the next session pushes the placement once it does.
	 */
	private suspend fun createProjectsOnServer(
		tree: ProjectTree,
		claimedNames: Set<String>,
		serverSyncData: BeginProjectsSyncResponse,
		serverKnownIds: Set<ProjectId>,
		ledger: BaselineLedger,
		onLog: OnSyncLog,
		onUnauthorized: suspend () -> Unit = {},
	) {
		val localOnly = tree.projects.filter { def ->
			def.id !in serverKnownIds && def.name !in claimedNames
		}

		localOnly.forEach { projectDef ->
			val projectName = projectDef.name
			// Checked up front: the server create runs before any setProjectId that would refuse it.
			if (projectsRepository.isProjectInUse(projectDef)) {
				placer.logInUse(projectDef, onLog)
				return@forEach
			}
			val outcome = try {
				val groupId = tree.placement[projectDef.id]?.takeIf { it in ledger.confirmedGroupIds }
				projectCreateResolver.create(projectDef, serverSyncData.syncId, groupId)
			} catch (_: ProjectInUseException) {
				placer.logInUse(projectDef, onLog)
				return@forEach
			}

			when (outcome) {
				is ServerCreateOutcome.Created -> {
					ledger.recordProject(outcome.projectId, outcome.baseline)
					if (outcome.reminted) {
						onLog(
							syncAccLogW(
								strRes.get(Res.string.sync_log_account_project_id_reminted, projectName)
							)
						)
					}
					onLog(
						syncAccLogI(
							strRes.get(
								Res.string.sync_log_account_project_create_server_success,
								projectName
							)
						)
					)
				}

				ServerCreateOutcome.DeletedElsewhere -> onLog(
					syncAccLogW(
						strRes.get(Res.string.sync_log_account_project_deleted_elsewhere, projectName)
					)
				)

				is ServerCreateOutcome.AdoptConflict -> onLog(
					syncAccLogW(
						strRes.get(
							Res.string.sync_log_account_project_adopt_conflict,
							projectName,
							outcome.heldBy.name,
						)
					)
				)

				ServerCreateOutcome.NeedsAccountSync ->
					error("Account sync may change a project's id, so it never needs account sync")

				is ServerCreateOutcome.Failed -> {
					Napier.e("Failed to create project on server: $projectName", outcome.error)
					onLog(
						syncAccLogE(
							strRes.get(
								Res.string.sync_log_account_project_create_server_failure,
								projectName
							)
						)
					)

					if (outcome.error.isAuthenticationFailure()) {
						onUnauthorized()
					}
				}
			}
		}
	}

	suspend fun deleteProject(project: ProjectDef) {
		accountSyncDatasource.update { syncData ->
			syncData.copy(
				projectsToDelete = syncData.projectsToDelete + project.id,
				projectsToRename = syncData.projectsToRename
					.filterNot { it.projectId == project.id }
					.toSet(),
				projectBaselines = syncData.projectBaselines - project.id,
			)
		}
	}

	/**
	 * Forgets the account-level queues and baselines, which describe one particular server and mean
	 * nothing to another.
	 */
	suspend fun deleteSyncData() {
		accountSyncDatasource.delete()
	}
}

internal fun Throwable?.isAuthenticationFailure(): Boolean =
	(this is HttpFailureException && statusCode == HttpStatusCode.Unauthorized)

/**
 * Every project id the server accounted for, live or tombstoned. Must be read from the raw
 * begin_sync response: `processProjectSyncData` drops projects this client has queued for
 * deletion, and a project missing for that reason is still known to the server, not dead.
 */
private fun BeginProjectsSyncResponse.knownProjectIds(): Set<ProjectId> =
	projects.mapTo(mutableSetOf()) { it.uuid } + deletedProjects