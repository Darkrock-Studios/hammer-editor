package com.darkrockstudios.apps.hammer.common.components.projectselection.storyideas

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.update
import com.darkrockstudios.apps.hammer.base.IdeaId
import com.darkrockstudios.apps.hammer.common.components.ComponentBase
import com.darkrockstudios.apps.hammer.common.data.ClientResult
import com.darkrockstudios.apps.hammer.common.data.ideasrepository.IdeaError
import com.darkrockstudios.apps.hammer.common.data.ideasrepository.IdeasRepository
import com.darkrockstudios.apps.hammer.common.data.ideasrepository.InvalidIdea
import com.darkrockstudios.apps.hammer.common.data.ideasrepository.idea.StoryIdea
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.inject

class StoryIdeasComponent(
	componentContext: ComponentContext,
) : StoryIdeas, ComponentBase(componentContext) {

	private val ideasRepository: IdeasRepository by inject()

	private val _state = MutableValue(StoryIdeas.State())
	override val state: Value<StoryIdeas.State> = _state

	init {
		scope.launch {
			ideasRepository.ideasFlow.collect { ideas ->
				withContext(dispatcherMain) {
					_state.update { it.copy(ideas = ideas) }
				}
			}
		}
		// Pick up any ideas edited on disk since the repository first loaded
		ideasRepository.loadIdeas()
	}

	override fun showCreate() {
		_state.update { it.copy(editor = StoryIdeas.Editor.Create) }
	}

	override fun editIdea(id: IdeaId) {
		val idea = _state.value.ideas.find { it.id == id } ?: return
		_state.update { it.copy(editor = StoryIdeas.Editor.Edit(idea)) }
	}

	override fun closeEditor() {
		_state.update { it.copy(editor = null) }
	}

	override fun suggestTags(prefix: String): List<String> {
		val cleaned = prefix.trim().removePrefix("#")
		if (cleaned.isEmpty()) return emptyList()
		return _state.value.ideas
			.flatMap { it.tags }
			.groupingBy { it }
			.eachCount()
			.filterKeys { it.startsWith(cleaned, ignoreCase = true) }
			.entries
			.sortedByDescending { it.value }
			.map { it.key }
	}

	override suspend fun createIdea(title: String?, content: String, tags: Set<String>): IdeaError {
		return ideasRepository.createIdea(content = content, title = title, tags = tags).toIdeaError()
	}

	override suspend fun saveIdea(id: IdeaId, title: String?, content: String, tags: Set<String>): IdeaError {
		val idea = ideasRepository.getIdeaById(id) ?: return IdeaError.EMPTY
		return ideasRepository.updateIdea(
			idea.copy(title = title, content = content, tags = tags)
		).toIdeaError()
	}

	override suspend fun deleteIdea(id: IdeaId) {
		ideasRepository.deleteIdea(id)
	}

	override suspend fun archiveIdea(id: IdeaId) {
		ideasRepository.archiveIdea(id)
	}

	override suspend fun unarchiveIdea(id: IdeaId) {
		ideasRepository.unarchiveIdea(id)
	}

	private fun ClientResult<StoryIdea>.toIdeaError(): IdeaError = when (this) {
		is ClientResult.Success -> IdeaError.NONE
		is ClientResult.Failure -> (exception as? InvalidIdea)?.error ?: IdeaError.EMPTY
	}
}
