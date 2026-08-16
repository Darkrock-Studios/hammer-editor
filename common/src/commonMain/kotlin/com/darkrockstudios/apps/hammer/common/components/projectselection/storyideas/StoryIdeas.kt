package com.darkrockstudios.apps.hammer.common.components.projectselection.storyideas

import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.base.IdeaId
import com.darkrockstudios.apps.hammer.common.data.CResult
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.ideasrepository.IdeaError
import com.darkrockstudios.apps.hammer.base.http.storyideas.StoryIdea
import com.darkrockstudios.apps.hammer.common.dependencyinjection.HammerComponent
import kotlinx.serialization.Serializable

interface StoryIdeas : HammerComponent {
	val state: Value<State>

	fun showCreate()
	fun editIdea(id: IdeaId)
	fun closeEditor()
	fun suggestTags(prefix: String): List<String>

	fun beginEdit()
	fun discardEdit()
	fun updateTitle(title: String)
	fun updateContent(content: String)
	fun updateTags(tags: List<String>)
	fun updateTagDraft(tagDraft: String)

	suspend fun saveDraft(): SaveResult
	suspend fun deleteIdea(id: IdeaId)
	suspend fun archiveIdea(id: IdeaId)
	suspend fun unarchiveIdea(id: IdeaId)

	/** Creates a project from the idea, seeding its content as the project's first Note. */
	suspend fun promoteIdea(id: IdeaId): CResult<ProjectDef>

	data class State(
		val ideas: List<StoryIdea> = emptyList(),
		val editor: Editor? = null,
		val draft: Draft? = null,
	)

	sealed class Editor {
		data object Create : Editor()
		data class Edit(val idea: StoryIdea) : Editor()
	}

	/**
	 * The editor's working copy, alongside the baseline it was seeded from. Owned by the component
	 * rather than the composable so an Android configuration change or process death can't take
	 * unsaved text with it.
	 *
	 * Set whenever an editor opens and left in place when it closes, so the outgoing editor still
	 * has something to render while it animates away. Only meaningful while [State.editor] is set.
	 */
	@Serializable
	data class Draft(
		val isEditing: Boolean,
		val title: String = "",
		val content: String = "",
		val tags: List<String> = emptyList(),
		/** Typed into the tag field but not yet committed to a chip; folded in on save. */
		val tagDraft: String = "",
		val savedTitle: String? = null,
		val savedContent: String = "",
		val savedTags: Set<String> = emptySet(),
	) {
		val isDirty: Boolean
			get() = isEditing && (
				title != savedTitle.orEmpty() ||
					content != savedContent ||
					tags.toSet() != savedTags ||
					tagDraft.isNotBlank()
				)
	}

	sealed interface SaveResult {
		data object Created : SaveResult
		data object Saved : SaveResult
		data class Failed(val error: IdeaError) : SaveResult
	}
}
