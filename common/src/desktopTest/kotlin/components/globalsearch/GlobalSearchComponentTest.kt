package components.globalsearch

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.essenty.backhandler.BackHandler
import com.arkivanov.essenty.lifecycle.Lifecycle
import com.arkivanov.essenty.statekeeper.StateKeeper
import com.darkrockstudios.apps.hammer.common.components.globalsearch.AnnotatedSnippet
import com.darkrockstudios.apps.hammer.common.components.globalsearch.GlobalSearch
import com.darkrockstudios.apps.hammer.common.components.globalsearch.GlobalSearchComponent
import com.darkrockstudios.apps.hammer.common.components.globalsearch.GlobalSearchState
import com.darkrockstudios.apps.hammer.common.components.globalsearch.SearchResult
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import io.mockk.*
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import utils.BaseTest
import kotlin.test.assertEquals
import kotlin.test.assertSame

class GlobalSearchComponentTest : BaseTest() {

	@MockK
	lateinit var backHandler: BackHandler

	@MockK
	lateinit var stateKeeper: StateKeeper

	@MockK
	lateinit var lifecycle: Lifecycle

	@MockK
	private lateinit var context: ComponentContext

	@MockK
	private lateinit var searchState: GlobalSearchState

	private val projectDef = ProjectDef(name = "Test", path = HPath("/p", "Test", false))
	private val state = MutableValue(GlobalSearch.State())

	@BeforeEach
	override fun setup() {
		super.setup()

		MockKAnnotations.init(this, relaxUnitFun = true)
		setupKoin()

		every { lifecycle.state } returns Lifecycle.State.STARTED
		every { context.lifecycle } returns lifecycle
		every { context.backHandler } returns backHandler
		every { context.stateKeeper } returns stateKeeper
		every { backHandler.register(any()) } just Runs
		every { searchState.state } returns state
	}

	private fun createComponent(
		onDismiss: () -> Unit = {},
		navigateToResult: (SearchResult) -> Unit = {},
	) = GlobalSearchComponent(
		componentContext = context,
		projectDef = projectDef,
		searchState = searchState,
		onDismiss = onDismiss,
		navigateToResult = navigateToResult,
	)

	@Test
	fun `state is sourced from the holder`() = runTest {
		val existingResults = listOf(
			SearchResult.Note(
				noteId = 1,
				title = "Old query",
				snippet = AnnotatedSnippet("matched text", 0, 7),
			)
		)
		state.value = GlobalSearch.State(query = "previous", results = existingResults)

		val component = createComponent()

		assertEquals("previous", component.state.value.query)
		assertEquals(existingResults, component.state.value.results)
	}

	@Test
	fun `onQueryChanged delegates to the holder`() = runTest {
		val component = createComponent()
		component.onQueryChanged("dragon")

		verify { searchState.setQuery("dragon") }
	}

	@Test
	fun `onResultClicked invokes navigation callback with the result`() = runTest {
		val captured = slot<SearchResult>()
		val callback = mockk<(SearchResult) -> Unit>(relaxed = true)
		val result = SearchResult.Note(
			noteId = 7,
			title = "T",
			snippet = AnnotatedSnippet("a", 0, 1),
		)

		val component = createComponent(navigateToResult = callback)
		component.onResultClicked(result)

		verify { callback.invoke(capture(captured)) }
		assertSame(result, captured.captured)
	}

	@Test
	fun `dismiss invokes the dismiss callback`() = runTest {
		val dismiss = mockk<() -> Unit>(relaxed = true)
		val component = createComponent(onDismiss = dismiss)
		component.dismiss()
		verify { dismiss.invoke() }
	}
}
