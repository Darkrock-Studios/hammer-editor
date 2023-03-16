package com.darkrockstudios.apps.hammer.common.data.projectsync

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsRepository

class ProjectSynchronizer(
    private val projectDef: ProjectDef,
    private val globalSettingsRepository: GlobalSettingsRepository,
) {
    suspend fun sync() {

    }

    private suspend fun syncProject() {
        val settings = globalSettingsRepository.serverSettings ?: throw IllegalStateException("Server settings missing")
        settings.url


    }
}