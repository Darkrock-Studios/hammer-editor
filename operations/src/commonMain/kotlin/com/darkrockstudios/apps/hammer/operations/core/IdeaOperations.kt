package com.darkrockstudios.apps.hammer.operations.core

import com.darkrockstudios.apps.hammer.base.IdeaId
import com.darkrockstudios.apps.hammer.base.http.storyideas.StoryIdea
import com.darkrockstudios.apps.hammer.common.data.ClientResult
import com.darkrockstudios.apps.hammer.common.data.CResult
import com.darkrockstudios.apps.hammer.common.data.ideasrepository.IdeaError
import com.darkrockstudios.apps.hammer.common.data.ideasrepository.IdeasRepository
import com.darkrockstudios.apps.hammer.operations.Access
import com.darkrockstudios.apps.hammer.operations.FromStdin
import com.darkrockstudios.apps.hammer.operations.Operation
import com.darkrockstudios.apps.hammer.operations.OperationScope
import com.darkrockstudios.apps.hammer.operations.invalidInput
import com.darkrockstudios.apps.hammer.operations.notFound
import com.darkrockstudios.apps.hammer.operations.operation
import kotlinx.serialization.Serializable

// Ideas belong to the account, so these take no project.
internal fun ideaOperations(): List<Operation<*, *>> = listOf(
	operation<IdeaCreateInput, Idea>(
		name = "idea.create",
		description = "Add a story idea.",
		access = Access.Write,
		scope = OperationScope.Content,
	) { input ->
		val ideas = koinGet<IdeasRepository>()
		ideas.requireValid(input.content, input.tags.toSet())
		// createIdea adds to the ideas in memory, so they must have loaded.
		ideas.ideasFlow.loaded("Ideas")
		Idea(ideas.createIdea(input.content, input.title, input.tags.toSet()).require())
	},
	operation<IdeaUpdateInput, Idea>(
		name = "idea.update",
		description = "Replace a story idea's text, and its title or tags when given. An empty title clears it.",
		access = Access.Write,
		scope = OperationScope.Content,
	) { input ->
		val ideas = koinGet<IdeasRepository>()
		val idea = requireIdea(input.id)
		val tags = input.tags?.toSet() ?: idea.tags
		ideas.requireValid(input.content, tags)
		Idea(ideas.updateIdea(idea.copy(content = input.content, title = input.title ?: idea.title, tags = tags)).require())
	},
	operation<IdeaInput, Idea>(
		name = "idea.archive",
		description = "Archive a story idea.",
		access = Access.Write,
		scope = OperationScope.Content,
	) { input ->
		Idea(koinGet<IdeasRepository>().archiveIdea(requireIdea(input.id).id).require())
	},
	operation<IdeaInput, Idea>(
		name = "idea.unarchive",
		description = "Return an archived story idea to the active ones.",
		access = Access.Write,
		scope = OperationScope.Content,
	) { input ->
		Idea(koinGet<IdeasRepository>().unarchiveIdea(requireIdea(input.id).id).require())
	},
	operation<IdeaInput, Idea>(
		name = "idea.delete",
		description = "Delete a story idea for good.",
		access = Access.Destructive,
		scope = OperationScope.Content,
	) { input ->
		val idea = requireIdea(input.id)
		koinGet<IdeasRepository>().deleteIdea(idea.id)
		Idea(idea)
	},
)

private suspend fun requireIdea(id: String): StoryIdea =
	koinGet<IdeasRepository>().ideasFlow.loaded("Ideas").find { it.id == IdeaId(id) } ?: notFound("No idea $id")

private fun IdeasRepository.requireValid(content: String, tags: Set<String>) {
	when (validateIdea(content, tags)) {
		IdeaError.NONE -> Unit
		IdeaError.EMPTY -> invalidInput("An idea needs text")
		IdeaError.TOO_LONG -> invalidInput("An idea can be at most ${StoryIdea.MAX_CONTENT_LENGTH} characters")
		IdeaError.TAG_TOO_LONG -> invalidInput("A tag is too long")
	}
}

private fun CResult<StoryIdea>.require(): StoryIdea =
	(this as? ClientResult.Success)?.data ?: error("Could not save the idea")

@Serializable
data class IdeaCreateInput(
	@FromStdin val content: String,
	val title: String? = null,
	val tags: List<String> = emptyList(),
)

@Serializable
data class IdeaUpdateInput(
	val id: String,
	@FromStdin val content: String,
	/** Left out, unchanged; empty, cleared. */
	val title: String? = null,
	val tags: List<String>? = null,
)

@Serializable
data class IdeaInput(val id: String)
