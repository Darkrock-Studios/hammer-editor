package com.darkrockstudios.apps.hammer.common.data.projectsync

import com.darkrockstudios.apps.hammer.MR
import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.http.ApiProjectDefinition
import com.darkrockstudios.apps.hammer.base.http.BeginProjectsSyncResponse
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.SyncedProjectDefinition
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsRepository
import com.darkrockstudios.apps.hammer.common.data.isSuccess
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import com.darkrockstudios.apps.hammer.common.server.ServerProjectsApi
import com.darkrockstudios.apps.hammer.common.util.NetworkConnectivity
import com.darkrockstudios.apps.hammer.common.util.StrRes
import io.github.aakira.napier.Napier
import korlibs.io.lang.InvalidArgumentException
import korlibs.io.util.UUID
import kotlinx.coroutines.yield
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path
import kotlin.coroutines.cancellation.CancellationException


class ClientProjectsSynchronizer(
	private val fileSystem: FileSystem,
	private val globalSettingsRepository: GlobalSettingsRepository,
	private val projectsRepository: ProjectsRepository,
	private val serverProjectsApi: ServerProjectsApi,
	private val networkConnectivity: NetworkConnectivity,
	private val json: Json,
	private val strRes: StrRes,
) {
	var initialSync = false

	fun isServerSynchronized(): Boolean {
		return (globalSettingsRepository.serverSettings?.userId ?: -1) > -1
	}

	suspend fun shouldAutoSync(): Boolean =
		globalSettingsRepository.globalSettings.automaticSyncing &&
			networkConnectivity.hasActiveConnection()

	suspend fun syncProjects(onLog: OnSyncLog): Boolean {
		onLog(syncAccLogI(strRes.get(MR.strings.sync_log_account_begin)))

		var syncId: String? = null
		return try {
			val result = serverProjectsApi.beginProjectsSync()
			if (result.isSuccess) {
				onLog(syncAccLogI(strRes.get(MR.strings.sync_log_account_server_data_loaded)))

				val serverSyncData = result.getOrThrow()
				syncId = serverSyncData.syncId

				val clientSyncData = loadSyncData()

				yield()

				syncRenamedProjects(clientSyncData, serverSyncData, onLog)

				yield()

				syncDeletedProjects(clientSyncData, serverSyncData, onLog)

				yield()

				val updatedServerSyncData = processProjectSyncData(serverSyncData, clientSyncData)

				val localProjects = projectsRepository.getProjects()
				syncCreatedProjects(clientSyncData, updatedServerSyncData, localProjects, onLog)

				yield()

				serverProjectsApi.endProjectsSync(syncId)
				onLog(syncAccLogI(strRes.get(MR.strings.sync_log_account_complete)))
				true
			} else {
				onLog(
					syncAccLogE(
						strRes.get(
							MR.strings.sync_log_account_failed,
							result.exceptionOrNull() ?: "---"
						)
					)
				)
				false
			}
		} catch (e: CancellationException) {
			Napier.i("Projects sync canceled: ${e.message}")
			throw e
		} catch (e: Exception) {
			Napier.e("Projects sync failed", e)

			syncId?.let {
				serverProjectsApi.endProjectsSync(syncId)
			}

			false
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
			projects = serverSyncData.projects.map { serverProj ->
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
		onLog: OnSyncLog,
	) {
		// Rename projects on the server
		clientSyncData.projectsToRename.forEach { (projectId, newName) ->
			val result = serverProjectsApi.renameProject(projectId, serverSyncData.syncId, newName)
			if (result.isSuccess) {
				onLog(
					syncAccLogI(
						strRes.get(
							MR.strings.sync_log_account_project_rename_server_success,
							projectId.id
						)
					)
				)
				updateSyncData { syncData ->
					syncData.copy(
						projectsToRename = syncData.projectsToRename
							.filterNot { it.projectId == projectId }.toSet(),
					)
				}
			} else {
				Napier.e("Failed to rename project: $projectId", result.exceptionOrNull())
				onLog(
					syncAccLogE(
						strRes.get(
							MR.strings.sync_log_account_project_rename_server_failure,
							projectId
						)
					)
				)
			}
		}
	}

	private suspend fun syncDeletedProjects(
		clientSyncData: ProjectsSynchronizationData,
		serverSyncData: BeginProjectsSyncResponse,
		onLog: OnSyncLog,
	) {
		deleteServerProjects(clientSyncData, serverSyncData, onLog)
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
							MR.strings.sync_log_account_project_delete_client,
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
		onLog: OnSyncLog
	) {
		clientSyncData.projectsToDelete.forEach { projectId ->
			val result = serverProjectsApi.deleteProject(projectId, serverSyncData.syncId)
			if (result.isSuccess) {
				onLog(
					syncAccLogI(
						strRes.get(
							MR.strings.sync_log_account_project_delete_server_success,
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
				Napier.e("Failed to delete project: $projectId", result.exceptionOrNull())
				onLog(
					syncAccLogE(
						strRes.get(
							MR.strings.sync_log_account_project_delete_server_failure,
							projectId
						)
					)
				)
			}
		}
	}

	private suspend fun syncCreatedProjects(
		clientSyncData: ProjectsSynchronizationData,
		serverSyncData: BeginProjectsSyncResponse,
		localProjects: List<ProjectDef>,
		onLog: OnSyncLog,
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

		createProjectsOnServer(localProjectsWithIds, clientSyncData, serverSyncData, onLog)

		createLocalProjectsFromServer(newServerProjects, onLog)
	}

	/**
	 * Create local projects from server
	 */
	private suspend fun createLocalProjectsFromServer(
		newServerProjects: List<ApiProjectDefinition>,
		onLog: OnSyncLog
	) {
		newServerProjects.forEach { serverProject ->
			val createResult = projectsRepository.createProject(serverProject.name)
			if (isSuccess(createResult)) {
				val projectDef = createResult.data
				projectsRepository.setProjectId(projectDef, serverProject.uuid)
				onLog(
					syncAccLogI(
						strRes.get(
							MR.strings.sync_log_account_project_create_client,
							serverProject.name
						)
					)
				)
			} else {
				onLog(
					syncAccLogW(
						strRes.get(
							MR.strings.sync_log_account_project_create_client_failure,
							serverProject.toString(),
						)
					)
				)
			}
		}
	}

	/**
	 * Create projects on the server which this client has created locally
	 */
	private suspend fun createProjectsOnServer(
		localProjectsWithIds: List<Pair<ProjectDef, ProjectId?>>,
		clientSyncData: ProjectsSynchronizationData,
		serverSyncData: BeginProjectsSyncResponse,
		onLog: OnSyncLog
	) {
		val localOnly = localProjectsWithIds.filter { (_, uuid) ->
			uuid == null
		}.map { it.first.name }

		val newLocalProjects = clientSyncData.projectsToCreate + localOnly
		// Create projects on the server
		newLocalProjects.forEach { projectName ->
			val result = serverProjectsApi.createProject(projectName, serverSyncData.syncId)
			if (result.isSuccess) {
				// Save the newly provisioned project id
				val response = result.getOrThrow()
				val projectDef = projectsRepository.getProjectDefinition(projectName)
				projectsRepository.setProjectId(projectDef, response.projectId)

				onLog(
					syncAccLogI(
						strRes.get(
							MR.strings.sync_log_account_project_create_server_success,
							projectName
						)
					)
				)
				updateSyncData { syncData ->
					syncData.copy(
						projectsToCreate = syncData.projectsToCreate - projectName,
					)
				}
			} else {
				onLog(
					syncAccLogE(
						strRes.get(
							MR.strings.sync_log_account_project_create_server_failure,
							projectName
						)
					)
				)
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
			if (serverProject.name != localProject.name) {
				val result = projectsRepository.renameProject(localProject, serverProject.name)
				if (isSuccess(result)) {
					onLog(
						syncAccLogI(
							strRes.get(
								MR.strings.sync_log_account_project_rename_client_from_server_success,
								serverProject.name
							)
						)
					)
				} else {
					onLog(
						syncAccLogE(
							strRes.get(
								MR.strings.sync_log_account_project_rename_client_from_server_failure,
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

	private fun getSyncDataPath(): Path =
		projectsRepository.getProjectsDirectory().toOkioPath() / SYNC_FILE_NAME

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
				UUID.invoke(it.id)
				true
			} catch (e: InvalidArgumentException) {
				Napier.w("Invalid UUID for deleted project: $it")
				false
			}
		}
		val deletedProjects = syncData.deletedProjects.filter {
			try {
				UUID.invoke(it.id)
				true
			} catch (e: InvalidArgumentException) {
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