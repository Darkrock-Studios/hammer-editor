package com.darkrockstudios.apps.hammer.common.data.ideasrepository

import com.darkrockstudios.apps.hammer.base.IdeaId
import com.darkrockstudios.apps.hammer.common.data.CResult
import com.darkrockstudios.apps.hammer.common.data.ClientResult
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.ideasrepository.idea.StoryIdea
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NotesDatasource
import com.darkrockstudios.apps.hammer.common.data.notesrepository.note.NoteContainer
import com.darkrockstudios.apps.hammer.common.data.notesrepository.note.NoteContent
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import net.peanuuutz.tomlkt.Toml
import okio.FileSystem
import kotlin.time.Clock

/**
 * "Create project from idea": creates a new project named from the idea, seeds the idea's
 * content (and tags) as the project's first Note, and stamps the idea as promoted.
 *
 * The idea itself keeps no reference to the created project — a name or id link would go
 * stale on rename/delete. Only the `promoted` timestamp records that it happened.
 */
class PromoteIdeaUseCase(
	private val projectsRepository: ProjectsRepository,
	private val ideasRepository: IdeasRepository,
	private val fileSystem: FileSystem,
	private val toml: Toml,
	private val clock: Clock,
) {
	suspend operator fun invoke(id: IdeaId): CResult<ProjectDef> {
		val idea = ideasRepository.getIdeaById(id)
			?: return CResult.failure(IllegalArgumentException("No idea for id: $id"))

		val baseName = deriveProjectName(idea)
		val created = createWithUniqueName(baseName)
		if (created is ClientResult.Failure) return created
		val projectDef = (created as ClientResult.Success).data

		// A freshly created project has no entities, so the seeded note takes the first id.
		val notesDatasource = NotesDatasource(projectDef, fileSystem, toml)
		notesDatasource.storeNote(
			NoteContainer(
				NoteContent(
					id = FIRST_ENTITY_ID,
					created = clock.now(),
					content = idea.content,
					tags = idea.tags,
				)
			)
		)

		ideasRepository.markPromoted(id)

		return CResult.success(projectDef)
	}

	private fun createWithUniqueName(baseName: String): CResult<ProjectDef> {
		var result = projectsRepository.createProject(baseName)
		var suffix = 2
		while (result.isFailure && suffix <= MAX_NAME_ATTEMPTS) {
			result = projectsRepository.createProject("$baseName $suffix")
			suffix++
		}
		return result
	}

	private fun deriveProjectName(idea: StoryIdea): String {
		val raw = idea.title
			?: idea.content.lineSequence()
				.map { it.trim().trim('#', '*', '_', '~', '>', ' ') }
				.firstOrNull { it.isNotEmpty() }
			?: ""
		val truncated = raw.take(MAX_DERIVED_NAME_LENGTH).trim().ifBlank { DEFAULT_PROJECT_NAME }
		return ProjectsRepository.toLocalSafeName(truncated)
	}

	companion object {
		private const val FIRST_ENTITY_ID = 1
		private const val MAX_DERIVED_NAME_LENGTH = 60
		private const val MAX_NAME_ATTEMPTS = 20
		private const val DEFAULT_PROJECT_NAME = "New Story"
	}
}
