import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.update
import com.darkrockstudios.apps.hammer.base.IdeaId
import com.darkrockstudios.apps.hammer.base.http.storyideas.StoryIdea
import com.darkrockstudios.apps.hammer.common.components.projectselection.storyideas.StoryIdeas
import com.darkrockstudios.apps.hammer.common.data.CResult
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import kotlin.time.Instant

internal fun storyIdea(
	uuid: String,
	content: String,
	title: String? = null,
	archived: Boolean = false,
) = StoryIdea(
	id = IdeaId(uuid),
	created = Instant.parse("2026-07-01T12:00:00Z"),
	updated = Instant.parse("2026-07-01T12:00:00Z"),
	title = title,
	content = content,
	archived = if (archived) Instant.parse("2026-07-02T12:00:00Z") else null,
)

/**
 * In-memory stand-in for `StoryIdeasComponent`. It owns the editor draft the same way the real
 * component does, which is the whole point: the UI must not keep unsaved text of its own.
 */
internal class FakeStoryIdeas(ideas: List<StoryIdea> = emptyList()) : StoryIdeas {
	private val _state = MutableValue(StoryIdeas.State(ideas = ideas))
	override val state: Value<StoryIdeas.State> = _state

	var createShownCount = 0
	var editedId: IdeaId? = null

	override fun showCreate() {
		createShownCount++
		_state.update {
			it.copy(editor = StoryIdeas.Editor.Create, draft = StoryIdeas.Draft(isEditing = true))
		}
	}

	override fun editIdea(id: IdeaId) {
		editedId = id
		val idea = _state.value.ideas.find { it.id == id } ?: return
		_state.update {
			it.copy(
				editor = StoryIdeas.Editor.Edit(idea),
				draft = StoryIdeas.Draft(
					isEditing = false,
					title = idea.title.orEmpty(),
					content = idea.content,
					tags = idea.tags.toList(),
					savedTitle = idea.title,
					savedContent = idea.content,
					savedTags = idea.tags,
				),
			)
		}
	}

	override fun closeEditor() = _state.update { it.copy(editor = null) }
	override fun suggestTags(prefix: String): List<String> = emptyList()
	override fun beginEdit() = updateDraft { it.copy(isEditing = true) }
	override fun discardEdit() = updateDraft { it.copy(isEditing = false) }
	override fun updateTitle(title: String) = updateDraft { it.copy(title = title) }
	override fun updateContent(content: String) = updateDraft { it.copy(content = content) }
	override fun updateTags(tags: List<String>) = updateDraft { it.copy(tags = tags) }
	override fun updateTagDraft(tagDraft: String) = updateDraft { it.copy(tagDraft = tagDraft) }
	override suspend fun saveDraft(): StoryIdeas.SaveResult = StoryIdeas.SaveResult.Saved
	override suspend fun deleteIdea(id: IdeaId) {}
	override suspend fun archiveIdea(id: IdeaId) {}
	override suspend fun unarchiveIdea(id: IdeaId) {}
	override suspend fun promoteIdea(id: IdeaId): CResult<ProjectDef> =
		CResult.failure(Exception("fake"))

	private fun updateDraft(transform: (StoryIdeas.Draft) -> StoryIdeas.Draft) {
		_state.update { state ->
			val draft = state.draft ?: return@update state
			state.copy(draft = transform(draft))
		}
	}
}
