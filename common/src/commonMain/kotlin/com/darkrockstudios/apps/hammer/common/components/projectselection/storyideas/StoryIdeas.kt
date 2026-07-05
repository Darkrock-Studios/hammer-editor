package com.darkrockstudios.apps.hammer.common.components.projectselection.storyideas

import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.base.IdeaId
import com.darkrockstudios.apps.hammer.common.data.CResult
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.ideasrepository.IdeaError
import com.darkrockstudios.apps.hammer.base.http.storyideas.StoryIdea
import com.darkrockstudios.apps.hammer.common.dependencyinjection.HammerComponent

interface StoryIdeas : HammerComponent {
	val state: Value<State>

	fun showCreate()
	fun editIdea(id: IdeaId)
	fun closeEditor()
	fun suggestTags(prefix: String): List<String>

	suspend fun createIdea(title: String?, content: String, tags: Set<String>): IdeaError
	suspend fun saveIdea(id: IdeaId, title: String?, content: String, tags: Set<String>): IdeaError
	suspend fun deleteIdea(id: IdeaId)
	suspend fun archiveIdea(id: IdeaId)
	suspend fun unarchiveIdea(id: IdeaId)

	/** Creates a project from the idea, seeding its content as the project's first Note. */
	suspend fun promoteIdea(id: IdeaId): CResult<ProjectDef>

	data class State(
		val ideas: List<StoryIdea> = emptyList(),
		val editor: Editor? = null,
	)

	sealed class Editor {
		data object Create : Editor()
		data class Edit(val idea: StoryIdea) : Editor()
	}
}
