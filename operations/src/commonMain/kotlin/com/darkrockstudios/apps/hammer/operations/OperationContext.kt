package com.darkrockstudios.apps.hammer.operations

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.ProjectSyncGateRepository
import com.darkrockstudios.apps.hammer.common.data.temporaryProjectTask
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
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
		val id = ProjectId(project)
		return projects.getProjects().find { it.id == id }
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

/**
 * The projects a write operation names, kept from syncing until [release]. Naming one that is
 * syncing fails at once rather than waiting: a sync can sit on a conflict for as long as the writer
 * takes to resolve it.
 */
internal class WritingProjects(
	private val projects: ProjectResolver,
	private val gate: ProjectSyncGateRepository,
) : ProjectResolver {

	private val admitted = MutableStateFlow(emptyList<ProjectDef>())

	override fun resolve(project: String): ProjectDef = projects.resolve(project).also(::admit)

	override suspend fun <T> withProject(project: String, block: suspend (OpenProject) -> T): T =
		projects.withProject(project) { open ->
			admit(open.def)
			block(open)
		}

	fun release() {
		admitted.getAndUpdate { emptyList() }.forEach(gate::endWrite)
	}

	private fun admit(def: ProjectDef) {
		if (!gate.beginWrite(def)) {
			throw OperationException(OperationException.Kind.Busy, "Project '${def.name}' is syncing; try again when it finishes.")
		}
		admitted.update { it + def }
	}
}
