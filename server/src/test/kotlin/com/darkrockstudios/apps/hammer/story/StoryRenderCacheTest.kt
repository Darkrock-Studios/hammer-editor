package com.darkrockstudios.apps.hammer.story

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.http.EntityHash
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals

class StoryRenderCacheTest {

	@TempDir
	lateinit var dir: Path

	private val projectId = ProjectId("test-project-uuid")

	private fun cache() = StoryRenderCache(dir)

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
		render: suspend () -> PaginatedStoryExportResult,
	) = getOrRender(
		projectId = projectId,
		projectName = projectName,
		page = page,
		wordsPerPage = 2000,
		sceneHashes = hashes,
		render = render,
	)

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
	fun `a corrupt entry is treated as a miss`() = runTest {
		val cache = cache()
		cache.render { result("<p>original</p>") }

		Files.list(dir).use { stream ->
			stream.forEach { Files.write(it, "not json".toByteArray()) }
		}

		val recovered = cache.render { result("<p>re-rendered</p>") }

		assertEquals("<p>re-rendered</p>", recovered.pageHtml)
	}
}
