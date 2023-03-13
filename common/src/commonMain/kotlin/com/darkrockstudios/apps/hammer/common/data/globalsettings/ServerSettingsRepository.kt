package com.darkrockstudios.apps.hammer.common.data.globalsettings

import com.akuleshov7.ktoml.Toml
import com.darkrockstudios.apps.hammer.common.getConfigDirectory
import com.darkrockstudios.apps.hammer.common.getDefaultRootDocumentDirectory
import okio.FileSystem
import okio.Path.Companion.toPath

class ServerSettingsRepository(
    private val fileSystem: FileSystem,
    private val toml: Toml
) {
    companion object {
        private const val FILE_NAME = "server.toml"
        private val CONFIG_PATH = getConfigDirectory().toPath() / FILE_NAME

        const val DEFAULT_PROJECTS_DIR = "HammerProjects"

        private fun defaultProjectDir() = getDefaultRootDocumentDirectory().toPath() / DEFAULT_PROJECTS_DIR

        fun createDefault(): GlobalSettings {
            return GlobalSettings(
                projectsDirectory = defaultProjectDir().toString()
            )
        }
    }
}