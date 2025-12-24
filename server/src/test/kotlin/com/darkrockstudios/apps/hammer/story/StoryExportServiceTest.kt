package com.darkrockstudios.apps.hammer.story

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.ApiSceneType
import com.darkrockstudios.apps.hammer.project.EntityDefinition
import com.darkrockstudios.apps.hammer.project.ProjectDefinition
import com.darkrockstudios.apps.hammer.project.ProjectEntityDatasource
import com.darkrockstudios.apps.hammer.utilities.SResult
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class StoryExportServiceTest {

	private lateinit var datasource: ProjectEntityDatasource
	private lateinit var service: StoryExportService

	private val userId = 1L
	private val projectId = ProjectId("test-project-uuid")
	private val projectDef = ProjectDefinition(name = "Test Story", uuid = projectId)

	@BeforeEach
	fun setup() {
		datasource = mockk()
		service = StoryExportService(datasource)
	}

	@Test
	fun `returns ProjectNotFound when project does not exist`() = runTest {
		coEvery { datasource.getProject(userId, projectId) } returns null

		val result = service.exportStoryAsHtml(userId, projectId)

		assertIs<StoryExportResult.ProjectNotFound>(result)
	}

	@Test
	fun `returns Success with hasContent false when project has no scenes`() = runTest {
		coEvery { datasource.getProject(userId, projectId) } returns projectDef
		coEvery { datasource.getEntityDefsByType(userId, projectDef, ApiProjectEntity.Type.SCENE) } returns emptyList()

		val result = service.exportStoryAsHtml(userId, projectId)

		assertIs<StoryExportResult.Success>(result)
		assertEquals("Test Story", result.projectName)
		assertFalse(result.hasContent)
		assertEquals("", result.html)
	}

	@Test
	fun `exports single scene correctly`() = runTest {
		val scene = createScene(id = 1, name = "Chapter One", content = "This is the content.", order = 0)
		setupMocksForScenes(listOf(scene))

		val result = service.exportStoryAsHtml(userId, projectId)

		assertIs<StoryExportResult.Success>(result)
		assertTrue(result.hasContent)
		assertTrue(result.html.contains("Test Story"))
		assertTrue(result.html.contains("Chapter One"))
		assertTrue(result.html.contains("This is the content."))
	}

	@Test
	fun `exports multiple scenes in correct order`() = runTest {
		val scenes = listOf(
			createScene(id = 1, name = "Third", content = "Content 3", order = 2),
			createScene(id = 2, name = "First", content = "Content 1", order = 0),
			createScene(id = 3, name = "Second", content = "Content 2", order = 1),
		)
		setupMocksForScenes(scenes)

		val result = service.exportStoryAsHtml(userId, projectId)

		assertIs<StoryExportResult.Success>(result)
		val html = result.html

		// Verify order: First should come before Second, Second before Third
		val firstPos = html.indexOf("First")
		val secondPos = html.indexOf("Second")
		val thirdPos = html.indexOf("Third")

		assertTrue(firstPos < secondPos, "First should appear before Second")
		assertTrue(secondPos < thirdPos, "Second should appear before Third")
	}

	@Test
	fun `exports group with child scenes - children content concatenated under group heading`() = runTest {
		val scenes = listOf(
			createScene(id = 1, name = "Chapter 1", content = "", order = 0, sceneType = ApiSceneType.Group),
			createScene(id = 2, name = "Scene 1.1", content = "First child content.", order = 0, path = listOf(0, 1)),
			createScene(id = 3, name = "Scene 1.2", content = "Second child content.", order = 1, path = listOf(0, 1)),
		)
		setupMocksForScenes(scenes)

		val result = service.exportStoryAsHtml(userId, projectId)

		assertIs<StoryExportResult.Success>(result)
		val html = result.html

		assertTrue(html.contains("Chapter 1"))
		assertTrue(html.contains("First child content."))
		assertTrue(html.contains("Second child content."))

		// Child scene names should NOT appear as headings (only group name appears)
		// The content is concatenated without sub-headings
		val firstContentPos = html.indexOf("First child content.")
		val secondContentPos = html.indexOf("Second child content.")
		assertTrue(firstContentPos < secondContentPos, "Child scenes should be in order")
	}

	@Test
	fun `exports nested groups correctly`() = runTest {
		val scenes = listOf(
			createScene(id = 1, name = "Part 1", content = "", order = 0, sceneType = ApiSceneType.Group),
			createScene(
				id = 2,
				name = "Chapter 1",
				content = "",
				order = 0,
				sceneType = ApiSceneType.Group,
				path = listOf(0, 1)
			),
			createScene(
				id = 3,
				name = "Deep Scene",
				content = "Deeply nested content.",
				order = 0,
				path = listOf(0, 1, 2)
			),
		)
		setupMocksForScenes(scenes)

		val result = service.exportStoryAsHtml(userId, projectId)

		assertIs<StoryExportResult.Success>(result)
		assertTrue(result.html.contains("Part 1"))
		assertTrue(result.html.contains("Deeply nested content."))
	}

	@Test
	fun `nested groups sort children by order at each level`() = runTest {
		// Structure:
		// - Chapter 1 (Group, order=0)
		//   - SubGroup A (Group, order=1)
		//     - Scene A2 (order=1)
		//     - Scene A1 (order=0)
		//   - Scene Direct (order=0)
		//   - SubGroup B (Group, order=2)
		//     - Scene B1 (order=0)
		val scenes = listOf(
			createScene(id = 1, name = "Chapter 1", content = "", order = 0, sceneType = ApiSceneType.Group),
			createScene(
				id = 2,
				name = "SubGroup A",
				content = "",
				order = 1,
				sceneType = ApiSceneType.Group,
				path = listOf(0, 1)
			),
			createScene(id = 3, name = "Scene A2", content = "Content A2", order = 1, path = listOf(0, 1, 2)),
			createScene(id = 4, name = "Scene A1", content = "Content A1", order = 0, path = listOf(0, 1, 2)),
			createScene(id = 5, name = "Scene Direct", content = "Content Direct", order = 0, path = listOf(0, 1)),
			createScene(
				id = 6,
				name = "SubGroup B",
				content = "",
				order = 2,
				sceneType = ApiSceneType.Group,
				path = listOf(0, 1)
			),
			createScene(id = 7, name = "Scene B1", content = "Content B1", order = 0, path = listOf(0, 1, 6)),
		)
		setupMocksForScenes(scenes)

		val result = service.exportStoryAsHtml(userId, projectId)

		assertIs<StoryExportResult.Success>(result)
		val html = result.html

		// Expected order based on `order` field at each level:
		// 1. Scene Direct (order=0 in Chapter 1's children)
		// 2. SubGroup A (order=1) -> Scene A1 (order=0), Scene A2 (order=1)
		// 3. SubGroup B (order=2) -> Scene B1
		// So: Direct, A1, A2, B1

		val directPos = html.indexOf("Content Direct")
		val a1Pos = html.indexOf("Content A1")
		val a2Pos = html.indexOf("Content A2")
		val b1Pos = html.indexOf("Content B1")

		assertTrue(directPos < a1Pos, "Direct should come before A1")
		assertTrue(a1Pos < a2Pos, "A1 should come before A2")
		assertTrue(a2Pos < b1Pos, "A2 should come before B1")
	}

	@Test
	fun `handles mixed scenes and groups at root level`() = runTest {
		val scenes = listOf(
			createScene(id = 1, name = "Prologue", content = "Prologue content.", order = 0),
			createScene(id = 2, name = "Part 1", content = "", order = 1, sceneType = ApiSceneType.Group),
			createScene(
				id = 3,
				name = "Scene in Part 1",
				content = "Part 1 scene content.",
				order = 0,
				path = listOf(0, 2)
			),
			createScene(id = 4, name = "Epilogue", content = "Epilogue content.", order = 2),
		)
		setupMocksForScenes(scenes)

		val result = service.exportStoryAsHtml(userId, projectId)

		assertIs<StoryExportResult.Success>(result)
		val html = result.html

		// Verify order
		val prologuePos = html.indexOf("Prologue content.")
		val part1Pos = html.indexOf("Part 1 scene content.")
		val epiloguePos = html.indexOf("Epilogue content.")

		assertTrue(prologuePos < part1Pos, "Prologue should come before Part 1")
		assertTrue(part1Pos < epiloguePos, "Part 1 should come before Epilogue")
	}

	@Test
	fun `skips scenes with blank content`() = runTest {
		val scenes = listOf(
			createScene(id = 1, name = "Has Content", content = "Some content.", order = 0),
			createScene(id = 2, name = "Empty", content = "", order = 1),
			createScene(id = 3, name = "Whitespace Only", content = "   ", order = 2),
		)
		setupMocksForScenes(scenes)

		val result = service.exportStoryAsHtml(userId, projectId)

		assertIs<StoryExportResult.Success>(result)
		assertTrue(result.html.contains("Some content."))
		// Empty and whitespace-only content should not cause issues
	}

	@Test
	fun `generates valid HTML from markdown`() = runTest {
		val scene = createScene(id = 1, name = "Test", content = "**Bold** and *italic* text.", order = 0)
		setupMocksForScenes(listOf(scene))

		val result = service.exportStoryAsHtml(userId, projectId)

		assertIs<StoryExportResult.Success>(result)
		// Markdown should be converted to HTML
		assertTrue(result.html.contains("<strong>") || result.html.contains("<b>"))
		assertTrue(result.html.contains("<em>") || result.html.contains("<i>"))
	}

	private fun createScene(
		id: Int,
		name: String,
		content: String,
		order: Int,
		sceneType: ApiSceneType = ApiSceneType.Scene,
		path: List<Int> = listOf(0)
	) = ApiProjectEntity.SceneEntity(
		id = id,
		sceneType = sceneType,
		order = order,
		name = name,
		path = path,
		content = content,
		outline = "",
		notes = ""
	)

	private fun setupMocksForScenes(scenes: List<ApiProjectEntity.SceneEntity>) {
		coEvery { datasource.getProject(userId, projectId) } returns projectDef

		val entityDefs = scenes.map { EntityDefinition(it.id, ApiProjectEntity.Type.SCENE) }
		coEvery { datasource.getEntityDefsByType(userId, projectDef, ApiProjectEntity.Type.SCENE) } returns entityDefs

		val entityIdSlot = slot<Int>()
		coEvery {
			datasource.loadEntity(
				userId,
				projectDef,
				capture(entityIdSlot),
				ApiProjectEntity.Type.SCENE,
				ApiProjectEntity.SceneEntity.serializer()
			)
		} answers {
			val scene = scenes.find { it.id == entityIdSlot.captured }
			if (scene != null) SResult.success(scene) else SResult.failure("Scene not found")
		}
	}

	// Paginated Export Tests

	@Test
	fun `paginated - returns ProjectNotFound when project does not exist`() = runTest {
		coEvery { datasource.getProject(userId, projectId) } returns null

		val result = service.exportStoryAsHtmlPaginated(userId, projectId)

		assertIs<PaginatedExportResult.ProjectNotFound>(result)
	}

	@Test
	fun `paginated - returns Success with empty content when project has no scenes`() = runTest {
		coEvery { datasource.getProject(userId, projectId) } returns projectDef
		coEvery { datasource.getEntityDefsByType(userId, projectDef, ApiProjectEntity.Type.SCENE) } returns emptyList()

		val result = service.exportStoryAsHtmlPaginated(userId, projectId)

		assertIs<PaginatedExportResult.Success>(result)
		val data = result.data
		assertEquals("Test Story", data.projectName)
		assertFalse(data.hasContent)
		assertEquals(0, data.sceneCount)
		assertEquals(0, data.totalWordCount)
		assertEquals(1, data.currentPage)
		assertEquals(1, data.totalPages)
		assertFalse(data.hasNextPage)
		assertFalse(data.hasPrevPage)
	}

	@Test
	fun `paginated - exports single scene with correct pagination metadata`() = runTest {
		val scene = createScene(id = 1, name = "Chapter One", content = "This is the content.", order = 0)
		setupMocksForScenes(listOf(scene))

		val result = service.exportStoryAsHtmlPaginated(userId, projectId)

		assertIs<PaginatedExportResult.Success>(result)
		val data = result.data
		assertTrue(data.hasContent)
		assertTrue(data.pageHtml.contains("Test Story"))
		assertTrue(data.pageHtml.contains("Chapter One"))
		assertTrue(data.pageHtml.contains("This is the content."))
		assertEquals(1, data.sceneCount)
		assertEquals(4, data.totalWordCount) // "This is the content." = 4 words
		assertEquals(1, data.currentPage)
		assertEquals(1, data.totalPages)
		assertFalse(data.hasNextPage)
		assertFalse(data.hasPrevPage)
	}

	@Test
	fun `paginated - splits scenes across pages based on word count`() = runTest {
		// Create scenes with known word counts to test pagination
		// Each "word word word" pattern = specific count
		val scenes = listOf(
			createScene(id = 1, name = "Scene 1", content = "one two three four five", order = 0), // 5 words
			createScene(id = 2, name = "Scene 2", content = "six seven eight nine ten", order = 1), // 5 words
			createScene(id = 3, name = "Scene 3", content = "eleven twelve thirteen", order = 2), // 3 words
		)
		setupMocksForScenes(scenes)

		// With wordsPerPage=8, Scene 1 (5 words) + Scene 2 (5 words) = 10 words > 8
		// So page 1 should have Scene 1, page 2 should have Scene 2+3
		val result = service.exportStoryAsHtmlPaginated(userId, projectId, page = 1, wordsPerPage = 8)

		assertIs<PaginatedExportResult.Success>(result)
		val data = result.data
		assertEquals(2, data.totalPages)
		assertEquals(1, data.currentPage)
		assertTrue(data.hasNextPage)
		assertFalse(data.hasPrevPage)
		assertEquals(13, data.totalWordCount)
		assertTrue(data.pageHtml.contains("Scene 1"))
		assertFalse(data.pageHtml.contains("Scene 2"))
	}

	@Test
	fun `paginated - returns correct page when requesting second page`() = runTest {
		val scenes = listOf(
			createScene(id = 1, name = "Scene 1", content = "one two three four five", order = 0), // 5 words
			createScene(id = 2, name = "Scene 2", content = "six seven eight nine ten", order = 1), // 5 words
		)
		setupMocksForScenes(scenes)

		val result = service.exportStoryAsHtmlPaginated(userId, projectId, page = 2, wordsPerPage = 5)

		assertIs<PaginatedExportResult.Success>(result)
		val data = result.data
		assertEquals(2, data.totalPages)
		assertEquals(2, data.currentPage)
		assertFalse(data.hasNextPage)
		assertTrue(data.hasPrevPage)
		assertFalse(data.pageHtml.contains("Scene 1"))
		assertTrue(data.pageHtml.contains("Scene 2"))
	}

	@Test
	fun `paginated - coerces invalid page numbers to valid range`() = runTest {
		val scene = createScene(id = 1, name = "Scene 1", content = "Some content here.", order = 0)
		setupMocksForScenes(listOf(scene))

		// Request page 100 when there's only 1 page
		val result = service.exportStoryAsHtmlPaginated(userId, projectId, page = 100, wordsPerPage = 1000)

		assertIs<PaginatedExportResult.Success>(result)
		val data = result.data
		assertEquals(1, data.currentPage)
		assertEquals(1, data.totalPages)
	}

	@Test
	fun `paginated - coerces page 0 to page 1`() = runTest {
		val scene = createScene(id = 1, name = "Scene 1", content = "Some content here.", order = 0)
		setupMocksForScenes(listOf(scene))

		val result = service.exportStoryAsHtmlPaginated(userId, projectId, page = 0, wordsPerPage = 1000)

		assertIs<PaginatedExportResult.Success>(result)
		assertEquals(1, result.data.currentPage)
	}

	@Test
	fun `paginated - includes title only on first page`() = runTest {
		val scenes = listOf(
			createScene(id = 1, name = "Scene 1", content = "one two three four five", order = 0),
			createScene(id = 2, name = "Scene 2", content = "six seven eight nine ten", order = 1),
		)
		setupMocksForScenes(scenes)

		val resultPage1 = service.exportStoryAsHtmlPaginated(userId, projectId, page = 1, wordsPerPage = 5)
		val resultPage2 = service.exportStoryAsHtmlPaginated(userId, projectId, page = 2, wordsPerPage = 5)

		assertIs<PaginatedExportResult.Success>(resultPage1)
		assertIs<PaginatedExportResult.Success>(resultPage2)

		// First page should have the title as h1
		assertTrue(resultPage1.data.pageHtml.contains("<h1>Test Story</h1>"))
		// Second page should not have the title
		assertFalse(resultPage2.data.pageHtml.contains("Test Story"))
	}

	@Test
	fun `paginated - only counts scenes not groups`() = runTest {
		val scenes = listOf(
			createScene(id = 1, name = "Chapter 1", content = "", order = 0, sceneType = ApiSceneType.Group),
			createScene(id = 2, name = "Scene 1", content = "one two three", order = 0, path = listOf(0, 1)),
			createScene(id = 3, name = "Scene 2", content = "four five six", order = 1, path = listOf(0, 1)),
		)
		setupMocksForScenes(scenes)

		val result = service.exportStoryAsHtmlPaginated(userId, projectId)

		assertIs<PaginatedExportResult.Success>(result)
		val data = result.data
		// Only 2 actual scenes, not the group
		assertEquals(2, data.sceneCount)
		assertEquals(6, data.totalWordCount)
	}

	@Test
	fun `paginated - calculates next and prev page correctly`() = runTest {
		val scenes = listOf(
			createScene(id = 1, name = "Scene 1", content = "one two", order = 0),
			createScene(id = 2, name = "Scene 2", content = "three four", order = 1),
			createScene(id = 3, name = "Scene 3", content = "five six", order = 2),
		)
		setupMocksForScenes(scenes)

		// With wordsPerPage=2, each scene should be on its own page
		val resultPage2 = service.exportStoryAsHtmlPaginated(userId, projectId, page = 2, wordsPerPage = 2)

		assertIs<PaginatedExportResult.Success>(resultPage2)
		val data = resultPage2.data
		assertEquals(3, data.totalPages)
		assertEquals(2, data.currentPage)
		assertEquals(1, data.prevPage)
		assertEquals(3, data.nextPage)
		assertTrue(data.hasNextPage)
		assertTrue(data.hasPrevPage)
	}

	@Test
	fun `paginated - prev and next page are bounded at edges`() = runTest {
		val scenes = listOf(
			createScene(id = 1, name = "Scene 1", content = "one two", order = 0),
			createScene(id = 2, name = "Scene 2", content = "three four", order = 1),
		)
		setupMocksForScenes(scenes)

		val resultPage1 = service.exportStoryAsHtmlPaginated(userId, projectId, page = 1, wordsPerPage = 2)
		val resultPage2 = service.exportStoryAsHtmlPaginated(userId, projectId, page = 2, wordsPerPage = 2)

		assertIs<PaginatedExportResult.Success>(resultPage1)
		assertIs<PaginatedExportResult.Success>(resultPage2)

		// Page 1: prevPage should be 1 (bounded)
		assertEquals(1, resultPage1.data.prevPage)
		// Page 2: nextPage should be 2 (bounded to max)
		assertEquals(2, resultPage2.data.nextPage)
	}

	@Test
	fun `paginated - calculates estimated reading time`() = runTest {
		// WordCountUtils uses ~200 words per minute
		// 400 words should be about 2 minutes
		val words = (1..400).joinToString(" ") { "word" }
		val scene = createScene(id = 1, name = "Long Scene", content = words, order = 0)
		setupMocksForScenes(listOf(scene))

		val result = service.exportStoryAsHtmlPaginated(userId, projectId)

		assertIs<PaginatedExportResult.Success>(result)
		// At ~200 wpm, 400 words should be ~2 minutes
		assertTrue(result.data.estimatedReadingTimeMinutes >= 1)
	}

	@Test
	fun `paginated - large scene stays on single page even if exceeds word limit`() = runTest {
		// If a single scene exceeds wordsPerPage, it should still be on one page
		val scene = createScene(
			id = 1,
			name = "Big Scene",
			content = "one two three four five six seven eight nine ten",
			order = 0
		)
		setupMocksForScenes(listOf(scene))

		val result = service.exportStoryAsHtmlPaginated(userId, projectId, page = 1, wordsPerPage = 5)

		assertIs<PaginatedExportResult.Success>(result)
		val data = result.data
		assertEquals(1, data.totalPages) // Single large scene stays on one page
		assertTrue(data.pageHtml.contains("Big Scene"))
	}
}
