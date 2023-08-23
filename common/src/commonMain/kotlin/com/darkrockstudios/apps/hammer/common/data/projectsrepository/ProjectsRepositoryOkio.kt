package com.darkrockstudios.apps.hammer.common.data.projectsrepository

import com.akuleshov7.ktoml.Toml
import com.darkrockstudios.apps.hammer.MR
import com.darkrockstudios.apps.hammer.common.components.projecteditor.metadata.Info
import com.darkrockstudios.apps.hammer.common.components.projecteditor.metadata.ProjectMetadata
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsRepository
import com.darkrockstudios.apps.hammer.common.data.projecteditorrepository.SceneEditorRepositoryOkio
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import io.github.aakira.napier.Napier
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import okio.FileSystem
import okio.IOException
import okio.Path.Companion.toPath

class ProjectsRepositoryOkio(
	private val fileSystem: FileSystem,
	private val toml: Toml,
	globalSettingsRepository: GlobalSettingsRepository,
) : ProjectsRepository() {

	private var globalSettings = globalSettingsRepository.globalSettings

	init {
		watchSettings(globalSettingsRepository)

		val projectsDir = getProjectsDirectory().toOkioPath()
		if (!fileSystem.exists(projectsDir)) {
			fileSystem.createDirectory(projectsDir)
		}
	}

	private fun watchSettings(globalSettingsRepository: GlobalSettingsRepository) {
		projectsScope.launch {
			globalSettingsRepository.globalSettingsUpdates.collect { newSettings ->
				globalSettings = newSettings
			}
		}
	}

	override fun getProjectsDirectory(): HPath {
		val projectsDir = globalSettings.projectsDirectory.toPath()

		if (!fileSystem.exists(projectsDir)) {
			fileSystem.createDirectories(projectsDir)
		}

		return projectsDir.toHPath()
	}

	override fun ensureProjectDirectory() {
		getProjectsDirectory()
	}

	override fun getProjects(projectsDir: HPath): List<ProjectDef> {
		val projPath = projectsDir.toOkioPath()
		return fileSystem.list(projPath)
			.filter { fileSystem.metadata(it).isDirectory }
			.filter { it.name.startsWith('.').not() }
			.map { path -> ProjectDef(path.name, path.toHPath()) }
	}

	override fun getProjectDirectory(projectName: String): HPath {
		val projectsDir = getProjectsDirectory().toOkioPath()
		val projectDir = projectsDir.div(projectName)
		return projectDir.toHPath()
	}

	override fun createProject(projectName: String): Result<Boolean> {
		val strippedName = projectName.trim()
		val result = validateFileName(strippedName)
		return if (result.isSuccess) {
			val projectsDir = getProjectsDirectory().toOkioPath()
			val newProjectDir = projectsDir.div(strippedName)
			if (fileSystem.exists(newProjectDir)) {
				Result.failure(ProjectCreationFailedException(MR.strings.create_project_error_already_exists))
			} else {
				fileSystem.createDirectory(newProjectDir)

				val newDef = ProjectDef(
					name = strippedName,
					path = newProjectDir.toHPath()
				)
				val metadataPath = SceneEditorRepositoryOkio.getMetadataPath(newDef)

				val metadata = ProjectMetadata(
					info = Info(
						created = Clock.System.now()
					)
				)
				val metalToml = toml.encodeToString(metadata)
				fileSystem.write(metadataPath.toOkioPath(), mustCreate = true) {
					writeUtf8(metalToml)
				}

				Result.success(true)
			}
		} else {
			Result.failure(
				ProjectCreationFailedException((result.exceptionOrNull() as? ValidationFailedException)?.errorMessage)
			)
		}
	}

	override fun deleteProject(projectDef: ProjectDef): Boolean {
		val projectDir = getProjectDirectory(projectDef.name).toOkioPath()
		return if (fileSystem.exists(projectDir)) {
			fileSystem.deleteRecursively(projectDir)
			true
		} else {
			false
		}
	}

	override suspend fun loadMetadata(projectDef: ProjectDef): ProjectMetadata? {
		val path = SceneEditorRepositoryOkio.getMetadataPath(projectDef).toOkioPath()

		val metadata = try {
			val metadataText = fileSystem.read(path) {
				readUtf8()
			}
			toml.decodeFromString<ProjectMetadata>(metadataText)
		} catch (e: IOException) {
			Napier.e("Failed to project metadata")
			null
		}

		return metadata
	}
}
