package components.globalsearch

import com.darkrockstudios.apps.hammer.common.components.globalsearch.AnnotatedSnippet
import com.darkrockstudios.apps.hammer.common.components.globalsearch.GlobalSearchFilter
import com.darkrockstudios.apps.hammer.common.components.globalsearch.GlobalSearchState
import com.darkrockstudios.apps.hammer.common.components.globalsearch.SearchResult
import com.darkrockstudios.apps.hammer.common.data.globalsearch.SearchProjectUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import utils.BaseTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GlobalSearchStateTest : BaseTest() {

	private lateinit var useCase: SearchProjectUseCase

	@BeforeEach
	override fun setup() {
		super.setup()
		setupKoin()
		useCase = mockk(relaxed = true)
		coEvery { useCase.search(any(), any()) } returns emptyList()
	}

	private fun note(id: Int) = SearchResult.Note(
		noteId = id,
		title = "Note $id",
		snippet = AnnotatedSnippet("snippet", 0, 0),
	)

	@Test
	fun `setQuery exposes parsed tags on state`() = runTest {
		val state = GlobalSearchState(useCase, mainContext = StandardTestDispatcher(testScheduler))
		state.setQuery("#a #b text")
		advanceUntilIdle()

		assertEquals(listOf("a", "b"), state.state.value.parsedTags)
		assertEquals("text", state.state.value.parsedText)
	}

	@Test
	fun `query shorter than min length clears results without searching`() = runTest {
		coEvery { useCase.search("dragon", any()) } returns listOf(note(1))
		val state = GlobalSearchState(useCase, mainContext = StandardTestDispatcher(testScheduler))
		state.setQuery("dragon")
		advanceUntilIdle()
		assertEquals(1, state.state.value.results.size)

		state.setQuery("a")
		advanceUntilIdle()

		assertEquals("a", state.state.value.query)
		assertEquals(emptyList(), state.state.value.results)
		assertTrue(!state.state.value.isSearching)
		coVerify(exactly = 0) { useCase.search("a", any()) }
	}

	@Test
	fun `setQuery debounces - only the latest query produces results`() = runTest {
		coEvery { useCase.search("alpha", any()) } returns listOf(note(1))
		coEvery { useCase.search("bravo", any()) } returns listOf(note(2))

		val state = GlobalSearchState(useCase, mainContext = StandardTestDispatcher(testScheduler))
		state.setQuery("alpha")
		advanceTimeBy(100)
		state.setQuery("bravo")
		advanceUntilIdle()

		val results = state.state.value.results.filterIsInstance<SearchResult.Note>()
		assertEquals(1, results.size)
		assertEquals(2, results.first().noteId)
		coVerify(exactly = 0) { useCase.search("alpha", any()) }
	}

	@Test
	fun `initial query runs a search on construction`() = runTest {
		coEvery { useCase.search("dragon", any()) } returns listOf(note(7))

		val state = GlobalSearchState(useCase, mainContext = StandardTestDispatcher(testScheduler), initialQuery = "dragon")
		advanceUntilIdle()

		val results = state.state.value.results.filterIsInstance<SearchResult.Note>()
		assertEquals(1, results.size)
		assertEquals(7, results.first().noteId)
	}
}
