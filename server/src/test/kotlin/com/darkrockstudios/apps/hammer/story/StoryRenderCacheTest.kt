package com.darkrockstudios.apps.hammer.story

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.http.EntityHash
import kotlinx.coroutines.test.runTest
import okio.Path
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class StoryRenderCacheTest {

	private val fileSystem = FakeFileSystem()
	private val dir = "/cache/story-html".toPath()

	private val projectId = ProjectId("test-project-uuid")

	private fun cache() = StoryRenderCache(fileSystem, dir)

	private fun cachedFiles(): List<Path> = fileSystem.listOrNull(dir).orEmpty()

	private fun result(html: String) = PaginatedStoryExportResult(
		projectName = "Test Story",
		pageHtml = html,
		hasContent = true,
		sceneCount = 2,
		totalWordCount = 100,
		currentPage = 1,
		totalPages = 3,
		hasNextPage = true,
		hasPrevPage = false,
		nextPage = 2,
		prevPage = 1,
		estimatedReadingTimeMinutes = 1,
	)

	private suspend fun StoryRenderCache.render(
		projectName: String = "Test Story",
		page: Int = 1,
		hashes: List<EntityHash> = listOf(EntityHash(1, "hash-one"), EntityHash(2, "hash-two")),
		complete: Boolean = true,
		render: suspend () -> PaginatedStoryExportResult,
	) = getOrRender(
		projectId = projectId,
		projectName = projectName,
		page = page,
		wordsPerPage = 2000,
		sceneHashes = hashes,
	) { StoryRender(render(), complete) }

	@Test
	fun `a second call with the same inputs is served from the cache`() = runTest {
		val cache = cache()
		var renderCount = 0

		val first = cache.render { renderCount++; result("<p>one</p>") }
		val second = cache.render { renderCount++; result("<p>one</p>") }

		assertEquals(1, renderCount, "an unchanged story should render once")
		assertEquals(first, second)
	}

	@Test
	fun `a round trip preserves every field`() = runTest {
		val cache = cache()
		val original = result("<p>prose</p>")

		cache.render { original }
		val cached = cache.render { error("should not re-render") }

		assertEquals(original, cached)
	}

	@Test
	fun `a changed scene hash renders fresh`() = runTest {
		val cache = cache()

		cache.render { result("<p>before</p>") }
		val after = cache.render(hashes = listOf(EntityHash(1, "hash-one"), EntityHash(2, "edited"))) {
			result("<p>after</p>")
		}

		assertEquals("<p>after</p>", after.pageHtml)
	}

	@Test
	fun `a removed scene renders fresh`() = runTest {
		val cache = cache()

		cache.render { result("<p>before</p>") }
		val after = cache.render(hashes = listOf(EntityHash(1, "hash-one"))) { result("<p>after</p>") }

		assertEquals("<p>after</p>", after.pageHtml)
	}

	@Test
	fun `a renamed project renders fresh`() = runTest {
		val cache = cache()

		cache.render { result("<p>before</p>") }
		val after = cache.render(projectName = "Renamed Story") { result("<p>after</p>") }

		assertEquals("<p>after</p>", after.pageHtml)
	}

	@Test
	fun `each page is cached separately`() = runTest {
		val cache = cache()

		cache.render(page = 1) { result("<p>page one</p>") }
		val second = cache.render(page = 2) { result("<p>page two</p>") }

		assertEquals("<p>page two</p>", second.pageHtml)
	}

	@Test
	fun `an incomplete render is served but never stored`() = runTest {
		val cache = cache()
		var renderCount = 0

		val first = cache.render(complete = false) { renderCount++; result("<p>missing a scene</p>") }
		val second = cache.render(complete = false) { renderCount++; result("<p>missing a scene</p>") }

		assertEquals("<p>missing a scene</p>", first.pageHtml, "the caller still gets the render")
		assertEquals("<p>missing a scene</p>", second.pageHtml)
		assertEquals(2, renderCount, "an incomplete render must not be cached")
		assertEquals(emptyList(), cachedFiles(), "nothing should be written")
	}

	@Test
	fun `a complete render replaces nothing an incomplete one left behind`() = runTest {
		val cache = cache()

		cache.render(complete = false) { result("<p>partial</p>") }
		val healed = cache.render(complete = true) { result("<p>whole</p>") }
		val served = cache.render { error("should be cached now") }

		assertEquals("<p>whole</p>", healed.pageHtml)
		assertEquals("<p>whole</p>", served.pageHtml)
	}

	@Test
	fun `a corrupt entry is treated as a miss`() = runTest {
		val cache = cache()
		cache.render { result("<p>original</p>") }

		cachedFiles().forEach { file ->
			fileSystem.write(file) { write("not json".toByteArray()) }
		}

		val recovered = cache.render { result("<p>re-rendered</p>") }

		assertEquals("<p>re-rendered</p>", recovered.pageHtml)
	}
}
