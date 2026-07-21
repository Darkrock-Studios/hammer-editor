package components.encyclopedia

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.backhandler.BackHandler
import com.arkivanov.essenty.lifecycle.Lifecycle
import com.arkivanov.essenty.statekeeper.StateKeeperDispatcher
import com.darkrockstudios.apps.hammer.common.components.encyclopedia.BrowseEntriesComponent
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaService
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContainer
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContent
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.data.tagindex.TagIndex
import com.darkrockstudios.apps.hammer.common.data.tagindex.TagIndexService
import com.darkrockstudios.apps.hammer.common.data.tagindex.TaggedEntityRef
import com.darkrockstudios.apps.hammer.common.data.tagindex.TaggedEntityType
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.dsl.bind
import org.koin.dsl.module
import utils.BaseTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BrowseEntriesComponentTest : BaseTest() {

	@MockK
	lateinit var backHandler: BackHandler

	@MockK
	lateinit var lifecycle: Lifecycle

	@MockK
	private lateinit var context: ComponentContext

	@MockK
	private lateinit var encyclopediaService: EncyclopediaService

	@MockK
	private lateinit var tagIndexService: TagIndexService

	private lateinit var entryListFlow: SharedFlow<List<EntryDef>>

	@BeforeEach
	override fun setup() {
		super.setup()

		MockKAnnotations.init(this, relaxUnitFun = true)

		val testModule = module {
			single { encyclopediaService } bind EncyclopediaService::class
			single { tagIndexService } bind TagIndexService::class
		}
		setupKoin(testModule)

		every { lifecycle.state } returns Lifecycle.State.STARTED

		every { context.lifecycle } returns lifecycle
		every { context.backHandler } returns backHandler
		every { context.stateKeeper } returns StateKeeperDispatcher()
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
	fun `Test Search - Ignores Whitespace`() = runTest {
		setupDefaultFlow()

		val comp = BrowseEntriesComponent(context, getProject1Def())
		comp.onCreate()

		advanceUntilIdle()

		comp.updateFilter(text = "bobrobert", type = null)
		val entries = comp.getFilteredEntries()
		assertEquals(1, entries.size)
		assertEquals("Bob Robert", entries.first().name)
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
	fun `Test Search - Multiple Tags Match All`() = runTest {
		setupDefaultFlow()

		val comp = BrowseEntriesComponent(context, getProject1Def())
		comp.onCreate()

		advanceUntilIdle()

		comp.updateFilter(text = "#two #three", type = null)
		val entries = comp.getFilteredEntries()
		assertEquals(1, entries.size)
		assertEquals("123 Hj ss", entries.first().name)
	}

	@OptIn(ExperimentalCoroutinesApi::class)
	@Test
	fun `Test Search - Tags And Text`() = runTest {
		setupDefaultFlow()

		val comp = BrowseEntriesComponent(context, getProject1Def())
		comp.onCreate()

		advanceUntilIdle()

		comp.updateFilter(text = "#three thing", type = null)
		val entries = comp.getFilteredEntries()
		assertEquals(1, entries.size)
		assertEquals("Big thing", entries.first().name)
	}

	@OptIn(ExperimentalCoroutinesApi::class)
	@Test
	fun `Test Add Tag To Search appends a hashtag token`() = runTest {
		setupDefaultFlow()

		val comp = BrowseEntriesComponent(context, getProject1Def())
		comp.onCreate()

		advanceUntilIdle()

		comp.addTagToSearch("three")
		assertEquals("#three", comp.filterText.value)
		assertEquals(1, comp.getFilteredEntries().count { it.name == "123 Hj ss" })

		// Already-present tags are not duplicated.
		comp.addTagToSearch("three")
		assertEquals("#three", comp.filterText.value)
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

		every { encyclopediaService.entryListFlow } returns entryListFlow

		val entryDefSlot = slot<EntryDef>()
		every { encyclopediaService.loadEntry(entryDef = capture(entryDefSlot)) } answers {
			entries.find { it.entry.id == entryDefSlot.captured.id }!!
		}

		every { tagIndexService.tagIndex } returns MutableStateFlow(buildTagIndex(entries))
	}

	private fun buildTagIndex(entries: List<EntryContainer>): TagIndex {
		val tagToEntities = mutableMapOf<String, MutableSet<TaggedEntityRef>>()
		entries.forEach { container ->
			container.entry.tags.forEach { tag ->
				tagToEntities.getOrPut(tag) { mutableSetOf() }
					.add(TaggedEntityRef(TaggedEntityType.Encyclopedia, container.entry.id))
			}
		}
		val counts = tagToEntities.mapValues { it.value.size }
		return TagIndex(
			tagToEntities = tagToEntities,
			countsByType = mapOf(TaggedEntityType.Encyclopedia to counts),
		)
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
