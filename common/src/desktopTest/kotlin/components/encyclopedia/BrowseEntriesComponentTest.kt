package components.encyclopedia

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.backhandler.BackHandler
import com.arkivanov.essenty.lifecycle.Lifecycle
import com.arkivanov.essenty.statekeeper.StateKeeper
import com.darkrockstudios.apps.hammer.common.components.encyclopedia.BrowseEntriesComponent
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaRepository
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContainer
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContent
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import getProject1Def
import io.mockk.MockKAnnotations
import io.mockk.Runs
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.slot
import korlibs.io.async.launch
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
			Triple(EntryType.PERSON, "Bob Robert", setOf("one", "two")),
			Triple(EntryType.PERSON, "Jason Splaptap", emptySet<String>()),
			Triple(EntryType.PERSON, "123 Hj ss", setOf("two")),
			Triple(EntryType.EVENT, "Big thing", setOf("two")),
			Triple(EntryType.PLACE, "Super Bob", emptySet<String>()),
			Triple(EntryType.THING, "Wobble Bobble", setOf("cool")),
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
	fun `Test Search - Empty Search`() = runTest {
		setupDefaultFlow()

		val comp = BrowseEntriesComponent(context, getProject1Def())
		comp.onCreate()

		advanceUntilIdle()

		comp.updateFilter(text = null, type = null)
		var entries = comp.getFilteredEntries()
		assertEquals(6, entries.size)

		comp.updateFilter(text = "", type = null)
		entries = comp.getFilteredEntries()
		assertEquals(6, entries.size)

		comp.updateFilter(text = "   	", type = null)
		entries = comp.getFilteredEntries()
		assertEquals(6, entries.size)
	}

	@OptIn(ExperimentalCoroutinesApi::class)
	@Test
	fun `Test Search - Type Search`() = runTest {
		setupDefaultFlow()

		val comp = BrowseEntriesComponent(context, getProject1Def())
		comp.onCreate()

		advanceUntilIdle()

		comp.updateFilter(text = null, type = EntryType.PERSON)
		var entries = comp.getFilteredEntries()
		assertEquals(4, entries.size)

		comp.updateFilter(text = null, type = EntryType.PLACE)
		entries = comp.getFilteredEntries()
		assertEquals(1, entries.size)

		comp.updateFilter(text = null, type = EntryType.EVENT)
		entries = comp.getFilteredEntries()
		assertEquals(1, entries.size)

		comp.updateFilter(text = null, type = EntryType.THING)
		entries = comp.getFilteredEntries()
		assertEquals(0, entries.size)
	}

	@OptIn(ExperimentalCoroutinesApi::class)
	@Test
	fun `Test Search - Simple`() = runTest {
		setupDefaultFlow()

		val comp = BrowseEntriesComponent(context, getProject1Def())
		comp.onCreate()

		advanceUntilIdle()

		comp.updateFilter(text = "Bob", type = EntryType.PERSON)
		val entries = comp.getFilteredEntries()
		assertEquals(2, entries.size)
	}

	@OptIn(ExperimentalCoroutinesApi::class)
	@Test
	fun `Test Search - Tags`() = runTest {
		setupDefaultFlow()

		val comp = BrowseEntriesComponent(context, getProject1Def())
		comp.onCreate()

		advanceUntilIdle()

		comp.updateFilter(text = "#one", type = null)
		var entries = comp.getFilteredEntries()
		assertEquals(1, entries.size)
		assertEquals("Bob Robert", entries.first().name)

		comp.updateFilter(text = "#two", type = null)
		entries = comp.getFilteredEntries()
		assertEquals(2, entries.size)

		comp.updateFilter(text = "#three", type = null)
		entries = comp.getFilteredEntries()
		assertEquals(3, entries.size)

		comp.updateFilter(text = "#three", type = EntryType.PERSON)
		entries = comp.getFilteredEntries()
		assertEquals(1, entries.size)

		comp.updateFilter(text = "#three 123", type = null)
		entries = comp.getFilteredEntries()
		assertEquals(1, entries.size)
		assertEquals("123 Hj ss", entries.first().name)

		comp.updateFilter(text = "123 #three", type = null)
		entries = comp.getFilteredEntries()
		assertEquals(1, entries.size)
		assertEquals("123 Hj ss", entries.first().name)

		comp.updateFilter(text = "123 #three", type = EntryType.PLACE)
		entries = comp.getFilteredEntries()
		assertEquals(0, entries.size)
	}

	@OptIn(ExperimentalCoroutinesApi::class)
	@Test
	fun `Test Search - Tags And Text`() = runTest {
		setupDefaultFlow()

		val comp = BrowseEntriesComponent(context, getProject1Def())
		comp.onCreate()

		advanceUntilIdle()

		comp.updateFilter(text = "#three thing", type = null)
		var entries = comp.getFilteredEntries()
		assertEquals(1, entries.size)
		assertEquals("Big thing", entries.first().name)

	}

	private suspend fun setupDefaultFlow() {
		setupFlow(
			Triple(EntryType.PERSON, "Bob Robert", setOf("one", "two")),
			Triple(EntryType.PERSON, "Jason Splaptap", emptySet<String>()),
			Triple(EntryType.PERSON, "123 Hj ss", setOf("two", "three")),
			Triple(EntryType.EVENT, "Big thing", setOf("three")),
			Triple(EntryType.PERSON, "Super Bob", emptySet<String>()),
			Triple(EntryType.PLACE, "Super Bobs House", setOf("three")),
		)
	}

	private suspend fun setupFlow(vararg data: Triple<EntryType, String, Set<String>>) {
		val flow = MutableSharedFlow<List<EntryDef>>(replay = 1, extraBufferCapacity = 1)
		entryListFlow = flow

		val projDef = getProject1Def()
		val entries = createFakeEntries(*data)

		launch(defaultTestDispatcher) {
			flow.emit(entries.map { it.toDef(projDef) })
		}

		every { encyclopediaRepository.entryListFlow } returns entryListFlow

		val entryDefSlot = slot<EntryDef>()
		every { encyclopediaRepository.loadEntry(entryDef = capture(entryDefSlot)) } answers {
			entries.find { it.entry.id == entryDefSlot.captured.id }!!
		}
	}

	private fun createFakeEntries(vararg data: Triple<EntryType, String, Set<String>>): List<EntryContainer> {
		var id = 1
		return data.map { (type, name, tags) ->
			EntryContainer(
				entry = EntryContent(
					id = id++,
					type = type,
					name = name,
					text = "Entry content $id $name $type",
					tags = tags
				)
			)
		}
	}
}