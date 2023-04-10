package com.darkrockstudios.apps.hammer.common.data.projectsrepository

import com.darkrockstudios.apps.hammer.common.components.projecteditor.metadata.ProjectMetadata
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.dependencyinjection.DISPATCHER_DEFAULT
import com.darkrockstudios.apps.hammer.common.fileio.HPath
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
	abstract fun createProject(projectName: String): Boolean
	abstract fun deleteProject(projectDef: ProjectDef): Boolean
	abstract suspend fun loadMetadata(projectDef: ProjectDef): ProjectMetadata?
	abstract fun ensureProjectDirectory()

	fun validateFileName(fileName: String?): Boolean {
		return if (fileName != null) {
			val wasValid = fileNameValidations.map {
				it.condition(fileName).also { isValid ->
					if (!isValid) Napier.w { "$fileName Failed validation: ${it.name}" }
				}
			}.reduce { acc, b -> acc && b }

			Napier.i("$fileName was valid: $wasValid")

			wasValid
		} else {
			false
		}
	}

	private val fileNameValidations = listOf(
		Validator("Was Blank") { it.isNotBlank() },
		Validator("Invalid Characters") { Regex("""[\da-zA-Z _']+""").matches(it) },
	)

	private data class Validator(
		val name: String,
		val condition: (String) -> Boolean,
	)
}