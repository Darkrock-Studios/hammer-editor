package com.darkrockstudios.apps.hammer.common.data.projectsync

import com.darkrockstudios.apps.hammer.base.http.GetProjectsResponse
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsRepository
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import com.darkrockstudios.apps.hammer.common.server.ServerProjectsApi
import io.github.aakira.napier.Napier
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path


class ClientProjectsSynchronizer(
	private val fileSystem: FileSystem,
	private val globalSettingsRepository: GlobalSettingsRepository,
	private val projectsRepository: ProjectsRepository,
	private val serverProjectsApi: ServerProjectsApi,
	private val json: Json
) {
	fun isServerSynchronized(): Boolean {
		return globalSettingsRepository.serverSettings != null
	}

	private fun performBackup() {
		Napier.i("Perform backup")
		// TODO: Backup the projects
	}

	suspend fun syncProjects() {
		Napier.i("Begin Sync")

		performBackup()

		val result = serverProjectsApi.getProjects()
		if (result.isSuccess) {
			val localProjects = projectsRepository.getProjects()
			val serverSyncData = result.getOrThrow()
			val clientSyncData = loadSyncData()

			syncDeletedProjects(clientSyncData, serverSyncData, localProjects)
			syncCreatedProjects(clientSyncData, serverSyncData, localProjects)

			Napier.i("Sync complete")
		} else {
			Napier.w("Failed to sync projects: ${result.exceptionOrNull()?.message}")
		}
	}

	private suspend fun syncDeletedProjects(
		clientSyncData: ProjectsSynchronizationData,
		serverSyncData: GetProjectsResponse,
		localProjects: List<ProjectDef>
	) {
		val newlyDeletedProjects = serverSyncData.deletedProjects.filter { projectName ->
			clientSyncData.deletedProjects.contains(projectName).not() &&
					clientSyncData.projectsToCreate.contains(projectName).not()
		}.mapNotNull { serverProjectName -> localProjects.find { it.name == serverProjectName } }

		// Delete projects on the server
		clientSyncData.projectsToDelete.forEach { projectName ->
			val result = serverProjectsApi.deleteProject(projectName)
			if (result.isSuccess) {
				updateSyncData { syncData ->
					syncData.copy(
						projectsToDelete = syncData.projectsToDelete - projectName,
						deletedProjects = syncData.deletedProjects + projectName,
					)
				}
			} else {
				Napier.w("Failed to delete project on server: $projectName")
			}
		}

		// Delete local projects from server
		Napier.i("Deleted projects: ${newlyDeletedProjects.size}")
		newlyDeletedProjects.forEach { projectName ->
			projectsRepository.deleteProject(projectName)
		}
	}

	private suspend fun syncCreatedProjects(
		clientSyncData: ProjectsSynchronizationData,
		serverSyncData: GetProjectsResponse,
		localProjects: List<ProjectDef>
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
			val result = serverProjectsApi.createProject(projectName)
			if (result.isSuccess) {
				updateSyncData { syncData ->
					syncData.copy(
						projectsToCreate = syncData.projectsToCreate - projectName,
					)
				}
			} else {
				Napier.w("Failed to create project on server: $projectName")
			}
		}

		// Create local projects from server
		Napier.i("New projects: ${newServerProjects.size}")
		newServerProjects.forEach { projectName ->
			projectsRepository.createProject(projectName)
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
				json.decodeFromString(syncDataJson)
			}
		} else {
			val newData = ProjectsSynchronizationData(
				deletedProjects = emptySet(),
				projectsToDelete = emptySet(),
				projectsToCreate = emptySet(),
			)
			saveSyncData(newData)

			newData
		}
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