package com.darkrockstudios.apps.hammer.common.data.projectsync

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsRepository
import com.darkrockstudios.apps.hammer.common.server.ServerProjectApi
import io.github.aakira.napier.Napier

class ProjectSynchronizer(
    private val projectDef: ProjectDef,
    private val globalSettingsRepository: GlobalSettingsRepository,
    private val serverProjectApi: ServerProjectApi
) {
    private val userId: Long
        get() = globalSettingsRepository.serverSettings?.userId
            ?: throw IllegalStateException("Server settings missing")

    suspend fun sync() {
        val result = serverProjectApi.hasProject(userId, projectDef.name)
        if (result.isSuccess) {
            val response = result.getOrThrow()
            if (response.exists) {
                syncProject()
            } else {
                uploadProject()
            }
        }
    }

    private suspend fun uploadProject() {
        Napier.d("Uploading project ${projectDef.name}")
    }

    private suspend fun syncProject() {
        Napier.d("Syncing project ${projectDef.name}")
    }
}