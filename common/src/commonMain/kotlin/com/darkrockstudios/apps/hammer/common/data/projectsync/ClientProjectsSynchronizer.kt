package com.darkrockstudios.apps.hammer.common.data.projectsync

import com.darkrockstudios.apps.hammer.base.http.BeginProjectsSyncResponse
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsRepository
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import com.darkrockstudios.apps.hammer.common.server.ServerProjectsApi
import com.darkrockstudios.apps.hammer.common.util.NetworkConnectivity
import io.github.aakira.napier.Napier
import kotlinx.coroutines.yield
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
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
	private val json: Json
) {
	var initialSync = false

	fun isServerSynchronized(): Boolean {
		return (globalSettingsRepository.serverSettings?.userId ?: -1) > -1
	}

	suspend fun shouldAutoSync(): Boolean = globalSettingsRepository.globalSettings.automaticSyncing &&
			networkConnectivity.hasActiveConnection()

	suspend fun syncProjects(onLog: OnSyncLog): Boolean {
		onLog(syncAccLogI("Begin Sync"))

		var syncId: String? = null
		return try {
			val result = serverProjectsApi.beginProjectsSync()
			if (result.isSuccess) {
				onLog(syncAccLogI("Got server data"))

				val serverSyncData = result.getOrThrow()
				syncId = serverSyncData.syncId

				val clientSyncData = loadSyncData()
				val localProjects = projectsRepository.getProjects()

				yield()

				syncDeletedProjects(clientSyncData, serverSyncData, localProjects, onLog)

				yield()

				syncCreatedProjects(clientSyncData, serverSyncData, localProjects, onLog)

				yield()

				serverProjectsApi.endProjectsSync(syncId)

				onLog(syncAccLogI("Account Sync complete"))
				true
			} else {
				onLog(syncAccLogE("Failed to sync projects: ${result.exceptionOrNull()?.message}"))
				false
			}
		} catch (e: Exception) {
			Napier.e("Projects sync failed", e)

			syncId?.let {
				serverProjectsApi.endProjectsSync(syncId)
			}

			if (e is CancellationException) throw e

			false
		}
	}

	private suspend fun syncDeletedProjects(
		clientSyncData: ProjectsSynchronizationData,
		serverSyncData: BeginProjectsSyncResponse,
		localProjects: List<ProjectDef>,
		onLog: OnSyncLog,
	) {
		val newlyDeletedProjects = serverSyncData.deletedProjects.filter { projectName ->
			clientSyncData.deletedProjects.contains(projectName).not() &&
					clientSyncData.projectsToCreate.contains(projectName).not()
		}.mapNotNull { serverProjectName -> localProjects.find { it.name == serverProjectName } }

		// Delete projects on the server
		clientSyncData.projectsToDelete.forEach { projectName ->
			val result = serverProjectsApi.deleteProject(projectName, serverSyncData.syncId)
			if (result.isSuccess) {
				onLog(syncAccLogI("Deleting server project: $projectName"))
				updateSyncData { syncData ->
					syncData.copy(
						projectsToDelete = syncData.projectsToDelete - projectName,
						deletedProjects = syncData.deletedProjects + projectName,
					)
				}
			} else {
				onLog(syncAccLogE("Failed to delete project on server: $projectName"))
			}
		}

		// Delete local projects from server
		newlyDeletedProjects.forEach { projectName ->
			onLog(syncAccLogI("Deleting local project: $projectName"))
			projectsRepository.deleteProject(projectName)
		}
	}

	private suspend fun syncCreatedProjects(
		clientSyncData: ProjectsSynchronizationData,
		serverSyncData: BeginProjectsSyncResponse,
		localProjects: List<ProjectDef>,
		onLog: OnSyncLog,
	) {
		val serverProjects = serverSyncData.projects
		val newServerProjects = serverProjects.filter { serverProject ->
			localProjects.none { localProject -> localProject.name == serverProject }
		}

		val localOnly = localProjects.filter { localProject ->
			serverProjects.none { serverProject -> serverProject == localProject.name }
		}.map { it.name }

		val newLocalProjects = clientSyncData.projectsToCreate + localOnly
		// Create projects on the server
		newLocalProjects.forEach { projectName ->
			val result = serverProjectsApi.createProject(projectName, serverSyncData.syncId)
			if (result.isSuccess) {
				onLog(syncAccLogI("Created project on server: $projectName"))
				updateSyncData { syncData ->
					syncData.copy(
						projectsToCreate = syncData.projectsToCreate - projectName,
					)
				}
			} else {
				onLog(syncAccLogE("Failed to create project on server: $projectName"))
			}
		}

		// Create local projects from server
		newServerProjects.forEach { projectName ->
			projectsRepository.createProject(projectName)
			onLog(syncAccLogI("Created local project: $projectName"))
		}
	}

	fun deleteProject(projectDef: ProjectDef) {
		updateSyncData { syncData ->
			syncData.copy(
				projectsToDelete = syncData.projectsToDelete + projectDef.name,
				projectsToCreate = syncData.projectsToCreate - projectDef.name,
			)
		}
	}

	fun createProject(projectName: String) {
		updateSyncData { syncData ->
			syncData.copy(
				projectsToCreate = syncData.projectsToCreate + projectName,
				projectsToDelete = syncData.projectsToDelete - projectName,
				deletedProjects = syncData.deletedProjects - projectName,
			)
		}
	}

	private fun getSyncDataPath(): Path = projectsRepository.getProjectsDirectory().toOkioPath() / SYNC_FILE_NAME

	private fun loadSyncData(): ProjectsSynchronizationData {
		val path = getSyncDataPath()
		return if (fileSystem.exists(path)) {
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