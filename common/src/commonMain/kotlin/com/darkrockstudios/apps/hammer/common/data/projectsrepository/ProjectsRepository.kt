package com.darkrockstudios.apps.hammer.common.data.projectsrepository

import com.darkrockstudios.apps.hammer.MR
import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.common.data.CResult
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.toMsg
import com.darkrockstudios.apps.hammer.common.dependencyinjection.DISPATCHER_DEFAULT
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import dev.icerock.moko.resources.StringResource
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named
import kotlin.coroutines.CoroutineContext

abstract class ProjectsRepository : KoinComponent {

	protected val dispatcherDefault: CoroutineContext by inject(named(DISPATCHER_DEFAULT))
	protected val projectsScope = CoroutineScope(dispatcherDefault)

	abstract fun getProjectsDirectory(): HPath
	abstract fun getProjects(projectsDir: HPath = getProjectsDirectory()): List<ProjectDef>
	abstract fun getProjectDirectory(projectName: String): HPath
	abstract fun getProjectDefinition(projectName: String): ProjectDef
	abstract fun createProject(projectName: String): CResult<ProjectDef>
	abstract fun deleteProject(projectDef: ProjectDef): Boolean
	abstract fun renameProject(projectDef: ProjectDef, newName: String): CResult<ProjectDef>
	abstract fun ensureProjectDirectory()

	//abstract fun renameProject(projectDef: ProjectDef, newName: String): CResult<Unit>
	abstract fun removeProjectId(projectDef: ProjectDef)
	abstract fun setProjectId(projectDef: ProjectDef, projectId: ProjectId)
	abstract fun getProjectId(projectDef: ProjectDef): ProjectId?
	abstract fun findProject(projectId: ProjectId): ProjectDef?
	abstract fun findProject(projectName: String): ProjectDef?

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
