package components.encyclopedia

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.backhandler.BackHandler
import com.arkivanov.essenty.lifecycle.Lifecycle
import com.arkivanov.essenty.statekeeper.StateKeeper
import com.darkrockstudios.apps.hammer.common.components.encyclopedia.BrowseEntriesComponent
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaRepository
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.soywiz.korio.async.launch
import getProject1Def
import io.mockk.MockKAnnotations
import io.mockk.Runs
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.koin.dsl.bind
import org.koin.dsl.module
import utils.BaseTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BrowseEntriesComponentTest : BaseTest() {

	@MockK
	lateinit var backHandler: BackHandler

	@MockK
	lateinit var stateKeeper: StateKeeper

	@MockK
	lateinit var lifecycleCallbacks: MutableList<Lifecycle.Callbacks>

	@MockK
	lateinit var lifecycle: Lifecycle

	@MockK
	private lateinit var context: ComponentContext

	@MockK
	private lateinit var encyclopediaRepository: EncyclopediaRepository

	private lateinit var entryListFlow: SharedFlow<List<EntryDef>>

	@Before
	override fun setup() {
		super.setup()

		MockKAnnotations.init(this, relaxUnitFun = true)

		val testModule = module {
			single { encyclopediaRepository } bind EncyclopediaRepository::class
		}
		setupKoin(testModule)

		every { context.lifecycle } returns lifecycle
		every { context.backHandler } returns backHandler
		every { context.stateKeeper } returns stateKeeper
		every { backHandler.register(any()) } just Runs
	}

	@OptIn(ExperimentalCoroutinesApi::class)
	@Test
	fun `Test Load Entries`() = runTest {
		val entries = listOf(
			EntryType.PERSON to "Bob Robert",
			EntryType.PERSON to "Jason Splaptap",
			EntryType.EVENT to "123 Hj ss",
			EntryType.PLACE to "Super Bob",
			EntryType.THING to "Wobble Bobble",
		)
		setupFlow(*entries.toTypedArray())

		val comp = BrowseEntriesComponent(context, getProject1Def())
		comp.onCreate()

		advanceUntilIdle()

		assertEquals(entries.size, comp.state.value.entryDefs.size)
		comp.state.value.entryDefs.forEach { entryDef ->
			val found = entries.find { it.first == entryDef.type && it.second == entryDef.name }
			assertTrue(found != null, "Entry not found!")
		}
	}

	@OptIn(ExperimentalCoroutinesApi::class)
	@Test
	fun `Test Search - Simple`() = runTest {
		setupFlow(
			EntryType.PERSON to "Bob Robert",
			EntryType.PERSON to "Jason Splaptap",
			EntryType.PERSON to "123 Hj ss",
			EntryType.PERSON to "Super Bob",
		)

		val comp = BrowseEntriesComponent(context, getProject1Def())
		comp.onCreate()

		advanceUntilIdle()

		comp.updateFilter(text = "Bob", type = EntryType.PERSON)
		val entries = comp.getFilteredEntries()
		assertEquals(2, entries.size)
	}

	private suspend fun setupFlow(vararg data: Pair<EntryType, String>) {
		val flow = MutableSharedFlow<List<EntryDef>>(replay = 1, extraBufferCapacity = 1)
		entryListFlow = flow

		launch(defaultTestDispatcher) {
			flow.emit(
				createFakeEntry(*data)
			)
		}

		every { encyclopediaRepository.entryListFlow } returns entryListFlow
	}

	private fun createFakeEntry(vararg data: Pair<EntryType, String>): List<EntryDef> {
		val projDef = getProject1Def()
		var id = 1
		return data.map { (type, name) ->
			EntryDef(
				projectDef = projDef,
				id = id++,
				type = type,
				name = name
			)
		}
	}
}