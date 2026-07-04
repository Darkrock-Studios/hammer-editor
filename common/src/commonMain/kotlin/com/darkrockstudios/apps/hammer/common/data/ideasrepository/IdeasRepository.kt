package com.darkrockstudios.apps.hammer.common.data.ideasrepository

import com.darkrockstudios.apps.hammer.base.IdeaId
import com.darkrockstudios.apps.hammer.common.data.CResult
import com.darkrockstudios.apps.hammer.common.data.ideasrepository.idea.StoryIdea
import com.darkrockstudios.apps.hammer.common.data.tagindex.cleanTags
import com.darkrockstudios.apps.hammer.common.dependencyinjection.DISPATCHER_DEFAULT
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named
import kotlin.coroutines.CoroutineContext
import kotlin.time.Clock

/**
 * Account-scoped (not project-scoped): ideas live at the projects root, outside any project,
 * so this is a global singleton rather than a `ProjectDefScope`d repository.
 */
class IdeasRepository(
	private val ideasDatasource: IdeasDatasource,
	private val clock: Clock,
) : KoinComponent {

	private val dispatcherDefault: CoroutineContext by inject(named(DISPATCHER_DEFAULT))
	private val ideasScope = CoroutineScope(dispatcherDefault)

	private var _ideas = listOf<StoryIdea>()

	private val _ideasFlow = MutableSharedFlow<List<StoryIdea>>(
		extraBufferCapacity = 1,
		onBufferOverflow = BufferOverflow.DROP_OLDEST,
		replay = 1,
	)
	val ideasFlow: SharedFlow<List<StoryIdea>> = _ideasFlow

	init {
		loadIdeas()
	}

	fun loadIdeas(onLoaded: (() -> Unit)? = null) {
		ideasScope.launch {
			updateIdeas(ideasDatasource.loadIdeas())
			onLoaded?.invoke()
		}
	}

	suspend fun getIdeaById(id: IdeaId): StoryIdea? = ideasFlow.first().find { it.id == id }

	suspend fun createIdea(
		content: String,
		title: String? = null,
		tags: Set<String> = emptySet(),
	): CResult<StoryIdea> {
		val result = validateIdea(content, tags)
		return if (result != IdeaError.NONE) {
			CResult.failure(InvalidIdea(result))
		} else {
			val now = clock.now()
			val idea = StoryIdea(
				id = IdeaId.randomUUID(),
				created = now,
				updated = now,
				title = cleanTitle(title),
				content = content,
				tags = cleanTags(tags),
			)
			ideasDatasource.createIdea(idea)
			updateIdeas(_ideas + idea)
			CResult.success(idea)
		}
	}

	suspend fun updateIdea(idea: StoryIdea): CResult<StoryIdea> {
		val result = validateIdea(idea.content, idea.tags)
		return if (result != IdeaError.NONE) {
			CResult.failure(InvalidIdea(result))
		} else {
			val updated = idea.copy(
				updated = clock.now(),
				title = cleanTitle(idea.title),
				tags = cleanTags(idea.tags),
			)
			ideasDatasource.updateIdea(updated)
			updateIdeas(_ideas.map { if (it.id == updated.id) updated else it })
			CResult.success(updated)
		}
	}

	suspend fun deleteIdea(id: IdeaId) {
		ideasDatasource.deleteIdea(id)
		updateIdeas(_ideas.filterNot { it.id == id })
	}

	suspend fun archiveIdea(id: IdeaId): CResult<StoryIdea> {
		val idea = getIdeaById(id) ?: return CResult.failure(IllegalArgumentException("No idea for id: $id"))
		return updateIdea(idea.copy(archived = clock.now()))
	}

	suspend fun unarchiveIdea(id: IdeaId): CResult<StoryIdea> {
		val idea = getIdeaById(id) ?: return CResult.failure(IllegalArgumentException("No idea for id: $id"))
		return updateIdea(idea.copy(archived = null))
	}

	suspend fun markPromoted(id: IdeaId): CResult<StoryIdea> {
		val idea = getIdeaById(id) ?: return CResult.failure(IllegalArgumentException("No idea for id: $id"))
		return updateIdea(idea.copy(promoted = clock.now()))
	}

	fun validateIdea(content: String, tags: Set<String> = emptySet()): IdeaError {
		val trimmed = content.trim()
		return if (trimmed.length > StoryIdea.MAX_CONTENT_LENGTH) {
			IdeaError.TOO_LONG
		} else if (trimmed.isEmpty()) {
			IdeaError.EMPTY
		} else if (tags.any { it.length > MAX_TAG_SIZE }) {
			IdeaError.TAG_TOO_LONG
		} else {
			IdeaError.NONE
		}
	}

	private fun cleanTitle(title: String?): String? = title?.trim()?.ifEmpty { null }

	private suspend fun updateIdeas(ideas: List<StoryIdea>) {
		_ideas = ideas
		_ideasFlow.emit(ideas)
	}

	companion object {
		const val MAX_TAG_SIZE = 32
	}
}
