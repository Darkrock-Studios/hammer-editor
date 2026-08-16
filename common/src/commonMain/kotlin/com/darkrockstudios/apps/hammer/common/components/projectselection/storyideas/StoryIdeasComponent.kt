package com.darkrockstudios.apps.hammer.common.components.projectselection.storyideas

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.update
import com.darkrockstudios.apps.hammer.base.IdeaId
import com.darkrockstudios.apps.hammer.common.components.ComponentBase
import com.darkrockstudios.apps.hammer.common.data.CResult
import com.darkrockstudios.apps.hammer.common.data.ClientResult
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.ideasrepository.IdeaError
import com.darkrockstudios.apps.hammer.common.data.ideasrepository.IdeasRepository
import com.darkrockstudios.apps.hammer.common.data.ideasrepository.InvalidIdea
import com.darkrockstudios.apps.hammer.common.data.ideasrepository.PromoteIdeaUseCase
import com.darkrockstudios.apps.hammer.base.http.storyideas.StoryIdea
import com.darkrockstudios.apps.hammer.common.data.tagindex.AccountTagService
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.koin.core.component.inject

class StoryIdeasComponent(
	componentContext: ComponentContext,
) : StoryIdeas, ComponentBase(componentContext) {

	private val ideasRepository: IdeasRepository by inject()
	private val promoteIdeaUseCase: PromoteIdeaUseCase by inject()
	private val accountTagService: AccountTagService by inject()

	private val _state = MutableValue(restoreState())
	override val state: Value<StoryIdeas.State> = _state

	init {
		stateKeeper.register(EDITOR_KEY, SavedEditor.serializer()) {
			val editor = _state.value.editor
			val draft = _state.value.draft
			if (editor == null || draft == null) {
				null
			} else {
				SavedEditor(idea = (editor as? StoryIdeas.Editor.Edit)?.idea, draft = draft)
			}
		}

		scope.launch {
			ideasRepository.ideasFlow.collect { ideas ->
				withContext(dispatcherMain) {
					_state.update { it.copy(ideas = ideas) }
				}
			}
		}
		// Pick up any ideas edited on disk since the repository first loaded
		ideasRepository.loadIdeas()
		scope.launch { accountTagService.refreshProjectTags() }
	}

	private fun restoreState(): StoryIdeas.State {
		val saved = stateKeeper.consume(EDITOR_KEY, SavedEditor.serializer())
			?: return StoryIdeas.State()

		return StoryIdeas.State(
			editor = saved.idea?.let { StoryIdeas.Editor.Edit(it) } ?: StoryIdeas.Editor.Create,
			draft = saved.draft,
		)
	}

	override fun showCreate() {
		_state.update {
			it.copy(
				editor = StoryIdeas.Editor.Create,
				draft = StoryIdeas.Draft(isEditing = true),
			)
		}
	}

	override fun editIdea(id: IdeaId) {
		val idea = _state.value.ideas.find { it.id == id } ?: return
		_state.update {
			it.copy(
				editor = StoryIdeas.Editor.Edit(idea),
				// An existing idea opens read-only; creating starts straight in edit mode.
				draft = idea.asDraft(isEditing = false),
			)
		}
	}

	override fun closeEditor() {
		// The draft is left behind on purpose: the detail pane cross-fades out after the editor
		// clears, and it still needs something to draw. Reopening always seeds a fresh draft.
		_state.update { it.copy(editor = null) }
	}

	override fun beginEdit() {
		updateDraft { it.copy(isEditing = true) }
	}

	override fun discardEdit() {
		if (_state.value.editor is StoryIdeas.Editor.Create) {
			closeEditor()
		} else {
			updateDraft { draft ->
				draft.copy(
					isEditing = false,
					title = draft.savedTitle.orEmpty(),
					content = draft.savedContent,
					tags = draft.savedTags.toList(),
					tagDraft = "",
				)
			}
		}
	}

	override fun updateTitle(title: String) = updateDraft { it.copy(title = title) }
	override fun updateContent(content: String) = updateDraft { it.copy(content = content) }
	override fun updateTags(tags: List<String>) = updateDraft { it.copy(tags = tags) }
	override fun updateTagDraft(tagDraft: String) = updateDraft { it.copy(tagDraft = tagDraft) }

	override fun suggestTags(prefix: String): List<String> {
		// Uncapped: the tag field filters out tags already on the idea, so a small hard cap here
		// could empty the strip when an idea's own tags fill the top ranks. The field's flow row
		// bounds what actually shows.
		return accountTagService.suggest(prefix, limit = Int.MAX_VALUE).map { it.tag }
	}

	override suspend fun saveDraft(): StoryIdeas.SaveResult {
		val editor = _state.value.editor ?: return StoryIdeas.SaveResult.Failed(IdeaError.EMPTY)
		val draft = _state.value.draft ?: return StoryIdeas.SaveResult.Failed(IdeaError.EMPTY)

		val title = draft.title.trim().ifEmpty { null }
		val pendingTag = draft.tagDraft.trim().removePrefix("#")
		val tags = if (pendingTag.isEmpty()) draft.tags.toSet() else draft.tags.toSet() + pendingTag

		val result = withContext(dispatcherDefault) {
			when (editor) {
				StoryIdeas.Editor.Create -> ideasRepository.createIdea(
					content = draft.content,
					title = title,
					tags = tags,
				)

				is StoryIdeas.Editor.Edit -> {
					val stored = ideasRepository.getIdeaById(editor.idea.id)
					if (stored == null) {
						CResult.failure<StoryIdea>(InvalidIdea(IdeaError.EMPTY))
					} else {
						ideasRepository.updateIdea(
							stored.copy(title = title, content = draft.content, tags = tags)
						)
					}
				}
			}
		}

		return when (result) {
			is ClientResult.Failure -> StoryIdeas.SaveResult.Failed(
				(result.exception as? InvalidIdea)?.error ?: IdeaError.EMPTY
			)

			is ClientResult.Success -> withContext(dispatcherMain) {
				val created = editor is StoryIdeas.Editor.Create
				// The editor can move on while the write is in flight; the result belongs to the
				// one that started it, so don't stamp it onto whatever is open now.
				if (isStillOpen(editor)) {
					if (created) {
						closeEditor()
					} else {
						// Re-baseline off what was actually stored, so the trimming and tag cleaning
						// the repository applies don't leave the editor looking dirty.
						updateDraft { result.data.asDraft(isEditing = false) }
					}
				}
				if (created) StoryIdeas.SaveResult.Created else StoryIdeas.SaveResult.Saved
			}
		}
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

	override suspend fun promoteIdea(id: IdeaId): CResult<ProjectDef> {
		return promoteIdeaUseCase(id)
	}

	private fun isStillOpen(editor: StoryIdeas.Editor): Boolean {
		val current = _state.value.editor
		return when (editor) {
			StoryIdeas.Editor.Create -> current is StoryIdeas.Editor.Create
			is StoryIdeas.Editor.Edit -> (current as? StoryIdeas.Editor.Edit)?.idea?.id == editor.idea.id
		}
	}

	private fun updateDraft(transform: (StoryIdeas.Draft) -> StoryIdeas.Draft) {
		_state.update { state ->
			val draft = state.draft ?: return@update state
			state.copy(draft = transform(draft))
		}
	}

	private fun StoryIdea.asDraft(isEditing: Boolean) = StoryIdeas.Draft(
		isEditing = isEditing,
		title = title.orEmpty(),
		content = content,
		tags = tags.toList(),
		savedTitle = title,
		savedContent = content,
		savedTags = tags,
	)

	@Serializable
	private data class SavedEditor(
		/** Null while creating a new idea. */
		val idea: StoryIdea?,
		val draft: StoryIdeas.Draft,
	)

	private companion object {
		const val EDITOR_KEY = "story-ideas-editor"
	}
}
