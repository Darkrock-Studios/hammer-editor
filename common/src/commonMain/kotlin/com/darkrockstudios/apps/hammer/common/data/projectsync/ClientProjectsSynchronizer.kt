package com.darkrockstudios.apps.hammer.common.data.projectsync

import com.darkrockstudios.apps.hammer.MR
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

				syncDeletedProjects(clientSyncData, serverSyncData, onLog)

				yield()

				// Remove client deleted projects the list of projects to sync
				val updatedServerSyncData = serverSyncData.copy(
					projects = serverSyncData.projects.filter { serverProj ->
						clientSyncData.projectsToDelete.none { clientProj ->
							clientProj.id == serverProj.uuid.id
						}
					}.toSet()
				)

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

	private suspend fun syncDeletedProjects(
		clientSyncData: ProjectsSynchronizationData,
		serverSyncData: BeginProjectsSyncResponse,
		onLog: OnSyncLog,
	) {
		val newlyDeletedProjects = serverSyncData.deletedProjects.filter { project ->
			clientSyncData.deletedProjects.contains(project).not()
		}

		// Delete projects on the server
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

		// Delete locally deleted projects from server
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

		// Create local projects from server
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

	fun deleteProject(project: SyncedProjectDefinition) {
		updateSyncData { syncData ->
			syncData.copy(
				projectsToDelete = syncData.projectsToDelete + project.projectId,
				projectsToCreate = syncData.projectsToCreate - project.projectDef.name,
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