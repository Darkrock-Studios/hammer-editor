package repositories.references

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.SceneSummary
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaRepository
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContainer
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContent
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.data.references.ReferenceIndexConfig
import com.darkrockstudios.apps.hammer.common.data.references.ReferenceIndexDatasource
import com.darkrockstudios.apps.hammer.common.data.references.ReferenceIndexRepository
import com.darkrockstudios.apps.hammer.common.data.references.ReferenceIndexService
import com.darkrockstudios.apps.hammer.common.data.references.ReferenceSourceType
import com.darkrockstudios.apps.hammer.common.data.references.WholeWordCaseSensitiveMatcher
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.scenemetadata.SceneMetadata
import com.darkrockstudios.apps.hammer.common.data.tree.ImmutableTree
import com.darkrockstudios.apps.hammer.common.data.tree.TreeValue
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import getProject1Def
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import utils.BaseTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.time.Clock

class ReferenceIndexServiceTest : BaseTest() {

	private val projectDef = getProject1Def()
	private lateinit var ffs: FakeFileSystem
	private lateinit var datasource: ReferenceIndexDatasource
	private lateinit var repository: ReferenceIndexRepository
	private lateinit var sceneEditor: SceneEditorRepository
	private lateinit var encyclopedia: EncyclopediaRepository

	@BeforeEach
	override fun setup() {
		super.setup()
		ffs = FakeFileSystem()
		val toml = createTomlSerializer()
		setupKoin(module {
			single { ffs }
			single { toml }
		})
		datasource = ReferenceIndexDatasource(ffs, toml, projectDef)
		repository = ReferenceIndexRepository(projectDef, datasource)
		sceneEditor = mockk(relaxed = true)
		encyclopedia = mockk(relaxed = true)
	}

	private fun makeService(
		config: ReferenceIndexConfig = ReferenceIndexConfig.default(),
	) = ReferenceIndexService(
		projectDef = projectDef,
		repository = repository,
		sceneEditorRepository = sceneEditor,
		encyclopediaRepository = encyclopedia,
		matcher = WholeWordCaseSensitiveMatcher(),
		config = config,
		clock = Clock.System,
	)

	private fun sceneItem(id: Int, name: String = "Scene $id") = SceneItem(
		projectDef = projectDef,
		type = SceneItem.Type.Scene,
		id = id,
		name = name,
		order = id,
	)

	private fun rootItem() = SceneItem(
		projectDef = projectDef,
		type = SceneItem.Type.Root,
		id = SceneItem.ROOT_ID,
		name = "",
		order = 0,
	)

	private fun stubSceneTree(scenes: List<Pair<SceneItem, SceneMetadata>>) {
		val children = scenes.mapIndexed { idx, (item, _) ->
			TreeValue(
				value = item,
				index = idx + 1,
				parent = 0,
				children = emptyList(),
				depth = 1,
				totalChildren = 0,
			)
		}
		val root = TreeValue(
			value = rootItem(),
			index = 0,
			parent = -1,
			children = children,
			depth = 0,
			totalChildren = children.size,
		)
		val summary = SceneSummary(
			sceneTree = ImmutableTree(root, totalChildren = children.size + 1),
			hasDirtyBuffer = emptySet(),
		)
		val flow = MutableSharedFlow<SceneSummary>(
			replay = 1, extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST
		).apply { tryEmit(summary) }
		every { sceneEditor.sceneListChannel } returns flow
		coEvery { sceneEditor.getArchivedScenes() } returns emptyList()
		for ((item, metadata) in scenes) {
			coEvery { sceneEditor.loadSceneMetadata(item.id) } returns metadata
		}
	}

	private fun stubEntries(entries: List<EntryContent>) {
		val defs = entries.map { it.toDef(projectDef) }
		val flow = MutableSharedFlow<List<EntryDef>>(
			replay = 1, extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST
		).apply { tryEmit(defs) }
		every { encyclopedia.entryListFlow } returns flow
		coEvery { encyclopedia.ensureEntriesLoaded() } returns defs
		for (entry in entries) {
			every { encyclopedia.loadEntry(entry.toDef(projectDef)) } returns EntryContainer(entry)
		}
	}

	private fun person(id: Int, name: String, aliases: List<String> = emptyList()) =
		EntryContent(id = id, name = name, type = EntryType.PERSON, text = "", tags = emptySet(), aliases = aliases)

	private fun place(id: Int, name: String) =
		EntryContent(id = id, name = name, type = EntryType.PLACE, text = "", tags = emptySet())

	@Test
	fun `recalculate builds inverted forward map from confirmed references`() = runTest(mainTestDispatcher) {
		stubSceneTree(
			listOf(
				sceneItem(10) to SceneMetadata(confirmedReferences = setOf(1, 2)),
				sceneItem(11) to SceneMetadata(confirmedReferences = setOf(2, 3)),
				sceneItem(12) to SceneMetadata(),
			)
		)
		stubEntries(emptyList())

		val index = makeService().recalculate()

		assertEquals(setOf(10), index.entryToScenes[1])
		assertEquals(setOf(10, 11), index.entryToScenes[2])
		assertEquals(setOf(11), index.entryToScenes[3])
		assertFalse(index.isDirty)
	}

	@Test
	fun `recalculate keeps confirmed entries even when names are not in text`() = runTest(mainTestDispatcher) {
		stubSceneTree(
			listOf(sceneItem(10) to SceneMetadata(confirmedReferences = setOf(1)))
		)
		stubEntries(emptyList())

		val index = makeService().recalculate()

		assertEquals(setOf(10), index.entryToScenes[1])
	}

	@Test
	fun `recalculate also walks archived scenes`() = runTest(mainTestDispatcher) {
		stubSceneTree(
			listOf(sceneItem(10) to SceneMetadata(confirmedReferences = setOf(1)))
		)
		val archived = sceneItem(99, "Archived").copy(archived = true)
		coEvery { sceneEditor.getArchivedScenes() } returns listOf(archived)
		coEvery { sceneEditor.loadSceneMetadata(99) } returns SceneMetadata(confirmedReferences = setOf(1))
		stubEntries(emptyList())

		val index = makeService().recalculate()

		assertEquals(setOf(10, 99), index.entryToScenes[1])
	}

	@Test
	fun `computeSuggestionsForScene returns plain matches when not confirmed or dismissed`() =
		runTest(mainTestDispatcher) {
			stubEntries(listOf(person(1, "Bob")))
			val service = makeService()

			val suggestions = service.computeSuggestionsForScene(
				sceneId = 10,
				sceneText = "Bob walked away.",
				metadata = SceneMetadata(),
			)

			assertEquals(1, suggestions.size)
			assertEquals(1, suggestions[0].entryId)
			assertEquals("Bob", suggestions[0].matchedAlias)
		}

	@Test
	fun `computeSuggestionsForScene excludes confirmed entries`() = runTest(mainTestDispatcher) {
		stubEntries(listOf(person(1, "Bob")))
		val service = makeService()

		val suggestions = service.computeSuggestionsForScene(
			sceneId = 10,
			sceneText = "Bob walked away.",
			metadata = SceneMetadata(confirmedReferences = setOf(1)),
		)

		assertTrue(suggestions.isEmpty())
	}

	@Test
	fun `computeSuggestionsForScene excludes dismissed entries`() = runTest(mainTestDispatcher) {
		stubEntries(listOf(person(1, "Bob")))
		val service = makeService()

		val suggestions = service.computeSuggestionsForScene(
			sceneId = 10,
			sceneText = "Bob walked away.",
			metadata = SceneMetadata(dismissedReferences = setOf(1)),
		)

		assertTrue(suggestions.isEmpty())
	}

	@Test
	fun `computeSuggestionsForScene attributes alias hits to the right entry`() = runTest(mainTestDispatcher) {
		stubEntries(listOf(person(1, "Robert", aliases = listOf("Bobby"))))
		val service = makeService()

		val suggestions = service.computeSuggestionsForScene(
			sceneId = 10,
			sceneText = "Bobby was tired.",
			metadata = SceneMetadata(),
		)

		assertEquals(1, suggestions.size)
		assertEquals(1, suggestions[0].entryId)
		assertEquals("Bobby", suggestions[0].matchedAlias)
	}

	@Test
	fun `computeSuggestionsForScene filters out non-enabled entry types`() = runTest(mainTestDispatcher) {
		stubEntries(
			listOf(
				person(1, "Bob"),
				place(2, "Mordor"),
			)
		)
		val service = makeService(
			ReferenceIndexConfig(
				enabledEntryTypes = setOf(EntryType.PERSON),
				enabledSourceTypes = setOf(ReferenceSourceType.Scene),
			)
		)

		val suggestions = service.computeSuggestionsForScene(
			sceneId = 10,
			sceneText = "Bob entered Mordor.",
			metadata = SceneMetadata(),
		)

		assertEquals(1, suggestions.size)
		assertEquals(1, suggestions[0].entryId)
	}

	@Test
	fun `computeSuggestionsForScene dedupes per entry on multiple hits`() = runTest(mainTestDispatcher) {
		stubEntries(listOf(person(1, "Bob")))
		val service = makeService()

		val suggestions = service.computeSuggestionsForScene(
			sceneId = 10,
			sceneText = "Bob and Bob and Bob.",
			metadata = SceneMetadata(),
		)

		assertEquals(1, suggestions.size)
		assertEquals(1, suggestions[0].entryId)
	}

	@Test
	fun `computeSuggestionsForScene returns empty list for empty text`() = runTest(mainTestDispatcher) {
		stubEntries(listOf(person(1, "Bob")))
		val service = makeService()

		val suggestions = service.computeSuggestionsForScene(
			sceneId = 10,
			sceneText = "",
			metadata = SceneMetadata(),
		)

		assertTrue(suggestions.isEmpty())
	}

	@Test
	fun `computeSuggestionsForScene returns empty list when no entries enabled`() =
		runTest(mainTestDispatcher) {
			stubEntries(emptyList())
			val service = makeService()

			val suggestions = service.computeSuggestionsForScene(
				sceneId = 10,
				sceneText = "Bob walked away.",
				metadata = SceneMetadata(),
			)

			assertTrue(suggestions.isEmpty())
		}
}
