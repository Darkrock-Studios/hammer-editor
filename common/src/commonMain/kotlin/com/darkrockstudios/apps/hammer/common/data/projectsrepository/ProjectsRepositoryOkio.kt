package com.darkrockstudios.apps.hammer.common.data.projectsrepository

import com.darkrockstudios.apps.hammer.MR
import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.common.components.storyeditor.metadata.Info
import com.darkrockstudios.apps.hammer.common.components.storyeditor.metadata.ProjectMetadata
import com.darkrockstudios.apps.hammer.common.data.CResult
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsRepository
import com.darkrockstudios.apps.hammer.common.data.isSuccess
import com.darkrockstudios.apps.hammer.common.data.migrator.PROJECT_DATA_VERSION
import com.darkrockstudios.apps.hammer.common.data.projectmetadatarepository.ProjectMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.projectmetadatarepository.loadProjectId
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import okio.FileSystem
import okio.Path.Companion.toPath

class ProjectsRepositoryOkio(
	private val fileSystem: FileSystem,
	globalSettingsRepository: GlobalSettingsRepository,
	private val projectsMetadataDatasource: ProjectMetadataDatasource
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

	override fun removeProjectId(projectDef: ProjectDef) {
		projectsMetadataDatasource.updateMetadata(projectDef) {
			it.copy(info = it.info.copy(serverProjectId = null))
		}
	}

	override fun setProjectId(projectDef: ProjectDef, projectId: ProjectId) {
		projectsMetadataDatasource.updateMetadata(projectDef) {
			it.copy(info = it.info.copy(serverProjectId = projectId))
		}
	}

	override fun getProjectId(projectDef: ProjectDef): ProjectId? {
		val metadata = projectsMetadataDatasource.loadMetadata(projectDef)
		return metadata.info.serverProjectId
	}

	override fun findProject(projectId: ProjectId): ProjectDef? {
		val allProjects = getProjects()
		val found = allProjects.find { project ->
			val id = projectsMetadataDatasource.loadProjectId(project)
			projectId == id
		}
		return found
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

	override fun getProjectDefinition(projectName: String): ProjectDef {
		val projectDir = getProjectDirectory(projectName).toOkioPath()
		return ProjectDef(projectName, projectDir.toHPath())
	}

	override fun createProject(projectName: String): CResult<ProjectDef> {
		val strippedName = projectName.trim()
		val result = validateFileName(strippedName)
		return if (isSuccess(result)) {
			val projectsDir = getProjectsDirectory().toOkioPath()
			val newProjectDir = projectsDir.div(strippedName)
			if (fileSystem.exists(newProjectDir)) {
				CResult.failure(ProjectCreationFailedException(MR.strings.create_project_error_already_exists))
			} else {
				fileSystem.createDirectory(newProjectDir)

				val newDef = ProjectDef(
					name = strippedName,
					path = newProjectDir.toHPath()
				)

				val metadata = ProjectMetadata(
					info = Info(
						created = Clock.System.now(),
						lastAccessed = Clock.System.now(),
						dataVersion = PROJECT_DATA_VERSION
					)
				)
				projectsMetadataDatasource.saveMetadata(metadata, newDef)

				CResult.success(newDef)
			}
		} else {
			CResult.failure(
				error = result.error,
				displayMessage = result.displayMessage,
				exception = ProjectCreationFailedException(result.displayMessage?.r)
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
}
