package com.darkrockstudios.apps.hammer.operations

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.projectmetadata.ProjectMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.data.temporaryProjectTask
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.scope.Scope

class OperationContext(
	val projects: ProjectResolver,
	/** For operations built on other operations, including plugin ones. */
	val operations: OperationRegistry,
)

interface ProjectResolver {
	/** The project named [project], or whose server project id is [project]. */
	fun resolve(project: String): ProjectDef

	/** Runs [block] with [project]'s Koin scope open, opening it just for the call if it is not already. */
	suspend fun <T> withProject(project: String, block: suspend (OpenProject) -> T): T
}

class OpenProject(val def: ProjectDef, val scope: Scope)

class KoinProjectResolver : ProjectResolver, KoinComponent {

	override fun resolve(project: String): ProjectDef {
		val projects = get<ProjectsRepository>()
		projects.findProject(project)?.let { return it }
		val metadata = get<ProjectMetadataDatasource>()
		val id = ProjectId(project)
		return projects.getProjects().find { metadata.readMetadata(it)?.info?.serverProjectId == id }
			?: notFound("No project named '$project'")
	}

	override suspend fun <T> withProject(project: String, block: suspend (OpenProject) -> T): T {
		val def = resolve(project)
		var result: Result<T>? = null
		temporaryProjectTask(def) { scope ->
			result = Result.success(block(OpenProject(def, scope)))
		}
		return result!!.getOrThrow()
	}
}
