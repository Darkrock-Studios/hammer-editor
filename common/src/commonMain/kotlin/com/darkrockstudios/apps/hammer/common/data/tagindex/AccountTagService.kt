package com.darkrockstudios.apps.hammer.common.data.tagindex

import com.darkrockstudios.apps.hammer.common.data.ideasrepository.IdeasRepository
import com.darkrockstudios.apps.hammer.common.data.projectdata.loadStoredProjectData
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.dependencyinjection.DISPATCHER_DEFAULT
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import net.peanuuutz.tomlkt.Toml
import okio.FileSystem
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named
import kotlin.coroutines.CoroutineContext

/**
 * Account-level tag vocabulary: the tags used on story ideas and on projects themselves
 * (`project_data.toml`), ranked by how often they are used. Ideas and projects share one
 * vocabulary — promoting an idea copies its tags onto the created project — so both tag
 * fields suggest from here. In-project *entity* tags are a separate per-project domain,
 * indexed by [TagIndexService].
 */
class AccountTagService(
	private val ideasRepository: IdeasRepository,
	private val projectsRepository: ProjectsRepository,
	private val fileSystem: FileSystem,
	private val toml: Toml,
) : KoinComponent {

	private val dispatcherDefault: CoroutineContext by inject(named(DISPATCHER_DEFAULT))
	private val serviceScope = CoroutineScope(dispatcherDefault)

	private val ideaTagCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
	private val projectTagCounts = MutableStateFlow<Map<String, Int>>(emptyMap())

	init {
		serviceScope.launch {
			ideasRepository.ideasFlow.collect { ideas ->
				ideaTagCounts.value = ideas.flatMap { it.tags }.groupingBy { it }.eachCount()
			}
		}
	}

	/**
	 * Re-scans project tags from disk. Idea tags stay fresh reactively via the ideas flow, but
	 * project-data writes have no account-wide change signal, so screens call this when they open.
	 */
	suspend fun refreshProjectTags() {
		projectTagCounts.value = projectsRepository.getProjects()
			.flatMap { loadStoredProjectData(it, fileSystem, toml).data.tags }
			.groupingBy { it }
			.eachCount()
	}

	/**
	 * Prefix-matching tag suggestions ranked by usage. [exclude] tags (already applied on the
	 * thing being tagged) are dropped **before** the [limit] cap — otherwise a few already-used
	 * tags can fill every slot and the caller's own "not already applied" filter then empties
	 * the strip.
	 */
	fun suggest(
		prefix: String,
		exclude: Set<String> = emptySet(),
		limit: Int = MAX_SUGGESTIONS,
	): List<TagCount> {
		val needle = normalizeTagNeedle(prefix)
		if (needle.isEmpty()) return emptyList()

		val merged = ideaTagCounts.value.toMutableMap()
		projectTagCounts.value.forEach { (tag, count) ->
			merged[tag] = (merged[tag] ?: 0) + count
		}

		return merged
			.filterKeys { it.startsWith(needle, ignoreCase = true) && it !in exclude }
			.map { (tag, count) -> TagCount(tag, count) }
			.sortedWith(compareByDescending<TagCount> { it.count }.thenBy { it.tag })
			.take(limit)
	}

	companion object {
		const val MAX_SUGGESTIONS = 5
	}
}
