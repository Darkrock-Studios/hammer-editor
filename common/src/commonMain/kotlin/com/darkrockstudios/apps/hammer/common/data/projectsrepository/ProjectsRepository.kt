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
import com.darkrockstudios.apps.hammer.common.data.toMsg
import com.darkrockstudios.apps.hammer.common.dependencyinjection.DISPATCHER_DEFAULT
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import dev.icerock.moko.resources.StringResource
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import okio.FileSystem
import okio.IOException
import okio.Path.Companion.toPath
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named
import kotlin.coroutines.CoroutineContext

class ProjectsRepository(
	private val fileSystem: FileSystem,
	globalSettingsRepository: GlobalSettingsRepository,
	private val projectsMetadataDatasource: ProjectMetadataDatasource
) : KoinComponent {

	protected val dispatcherDefault: CoroutineContext by inject(named(DISPATCHER_DEFAULT))
	protected val projectsScope = CoroutineScope(dispatcherDefault)

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

	fun getProjectsDirectory(): HPath {
		val projectsDir = globalSettings.projectsDirectory.toPath()

		if (!fileSystem.exists(projectsDir)) {
			fileSystem.createDirectories(projectsDir)
		}

		return projectsDir.toHPath()
	}

	fun ensureProjectDirectory() {
		getProjectsDirectory()
	}

	fun removeProjectId(projectDef: ProjectDef) {
		projectsMetadataDatasource.updateMetadata(projectDef) {
			it.copy(info = it.info.copy(serverProjectId = null))
		}
	}

	fun setProjectId(projectDef: ProjectDef, projectId: ProjectId) {
		projectsMetadataDatasource.updateMetadata(projectDef) {
			it.copy(info = it.info.copy(serverProjectId = projectId))
		}
	}

	fun getProjectId(projectDef: ProjectDef): ProjectId? {
		val metadata = projectsMetadataDatasource.loadMetadata(projectDef)
		return metadata.info.serverProjectId
	}

	fun findProject(projectId: ProjectId): ProjectDef? {
		val allProjects = getProjects()
		val found = allProjects.find { project ->
			val id = projectsMetadataDatasource.loadProjectId(project)
			projectId == id
		}
		return found
	}

	fun findProject(projectName: String): ProjectDef? {
		val allProjects = getProjects()
		val found = allProjects.find { project -> project.name == projectName }
		return found
	}

	fun getProjects(projectsDir: HPath = getProjectsDirectory()): List<ProjectDef> {
		val projPath = projectsDir.toOkioPath()
		return fileSystem.list(projPath)
			.filter { fileSystem.metadata(it).isDirectory }
			.filter { it.name.startsWith('.').not() }
			.map { path -> ProjectDef(path.name, path.toHPath()) }
	}

	fun getProjectDirectory(projectName: String): HPath {
		val projectsDir = getProjectsDirectory().toOkioPath()
		val projectDir = projectsDir.div(projectName)
		return projectDir.toHPath()
	}

	fun getProjectDefinition(projectName: String): ProjectDef {
		val projectDir = getProjectDirectory(projectName).toOkioPath()
		return ProjectDef(projectName, projectDir.toHPath())
	}

	fun createProject(projectName: String): CResult<ProjectDef> {
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

	fun renameProject(projectDef: ProjectDef, newName: String): CResult<ProjectDef> {
		if (validateFileName(newName).isFailure) {
			return CResult.failure(ProjectRenameFailed(ProjectRenameFailed.Reason.InvalidName))
		}

		val projectDir = getProjectDirectory(projectDef.name).toOkioPath()
		val newProjectDir = getProjectDirectory(newName).toOkioPath()
		return if (fileSystem.exists(projectDir)) {
			if (fileSystem.exists(newProjectDir).not()) {
				try {
					fileSystem.atomicMove(source = projectDir, target = newProjectDir)
					CResult.success(ProjectDef(newName, newProjectDir.toHPath()))
				} catch (e: IOException) {
					CResult.failure(ProjectRenameFailed(ProjectRenameFailed.Reason.MoveFailed))
				}
			} else {
				CResult.failure(ProjectRenameFailed(ProjectRenameFailed.Reason.AlreadyExists))
			}
		} else {
			CResult.failure(ProjectRenameFailed(ProjectRenameFailed.Reason.SourceDoesNotExist))
		}
	}

	fun deleteProject(projectDef: ProjectDef): Boolean {
		val projectDir = getProjectDirectory(projectDef.name).toOkioPath()
		return if (fileSystem.exists(projectDir)) {
			fileSystem.deleteRecursively(projectDir)
			true
		} else {
			false
		}
	}

	private data class Validator(
		val name: String,
		val errorMessage: StringResource,
		val condition: (String) -> Boolean,
	)

	companion object {
		const val MAX_FILENAME_LENGTH = 128

		private val fileNameValidations = listOf(
			Validator(
				"Was Blank",
				MR.strings.create_project_error_blank
			) { it.isNotBlank() },
			Validator(
				"Invalid Characters",
				MR.strings.create_project_error_invalid_characters
			) { Regex("""[\d\p{L}+ _']+""").matches(it) },
			Validator(
				"Too Long",
				MR.strings.create_project_error_too_long
			) { it.length <= MAX_FILENAME_LENGTH },
		)

		fun validateFileName(fileName: String?): CResult<Unit> {
			return if (fileName != null) {
				var error: StringResource? = null
				for (validator in fileNameValidations) {
					if (validator.condition(fileName).not()) {
						error = validator.errorMessage
						break
					}
				}

				if (error == null) {
					Napier.i("$fileName was valid")
					CResult.success()
				} else {
					Napier.i("$fileName was invalid: $error")
					CResult.failure(ValidationFailedException(error))
				}
			} else {
				CResult.failure(
					error = "",
					displayMessage = MR.strings.create_project_error_null_filename.toMsg(),
					exception = ValidationFailedException(MR.strings.create_project_error_null_filename)
				)
			}
		}
	}
}
