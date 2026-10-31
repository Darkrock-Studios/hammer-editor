package usecases.entitycatalog

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaRepository
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContainer
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContent
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.data.entitycatalog.BuildEntityCatalogUseCase
import com.darkrockstudios.apps.hammer.common.data.entitycatalog.CatalogFilter
import com.darkrockstudios.apps.hammer.common.data.entitycatalog.CatalogItem
import com.darkrockstudios.apps.hammer.common.data.entitycatalog.CatalogKind
import com.darkrockstudios.apps.hammer.common.data.entitycatalog.CatalogRef
import com.darkrockstudios.apps.hammer.common.data.entitycatalog.filterCatalog
import com.darkrockstudios.apps.hammer.common.data.entitycatalog.find
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.ImagesRepository
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NotesRepository
import com.darkrockstudios.apps.hammer.common.data.notesrepository.note.NoteContainer
import com.darkrockstudios.apps.hammer.common.data.notesrepository.note.NoteContent
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneRepository
import com.darkrockstudios.apps.hammer.common.data.tagindex.TagIndex
import com.darkrockstudios.apps.hammer.common.data.tagindex.TagIndexService
import com.darkrockstudios.apps.hammer.common.data.tagindex.TaggedEntityRef
import com.darkrockstudios.apps.hammer.common.data.tagindex.TaggedEntityType
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineContainer
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEvent
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineRepository
import com.darkrockstudios.apps.hammer.common.data.tree.Tree
import com.darkrockstudios.apps.hammer.common.data.tree.TreeNode
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testProjectId
import utils.BaseTest
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock

class EntityCatalogTest : BaseTest() {

	private val projectDef = ProjectDef(testProjectId("Test"), "Test", HPath("/projects/Test", "Test", false))

	private lateinit var encyclopedia: EncyclopediaRepository
	private lateinit var scenes: SceneRepository
	private lateinit var notes: NotesRepository
	private lateinit var timeline: TimeLineRepository
	private lateinit var tagIndex: TagIndexService
	private lateinit var images: ImagesRepository

	@BeforeEach
	override fun setup() {
		super.setup()
		setupKoin()
		encyclopedia = mockk(relaxed = true)
		scenes = mockk(relaxed = true)
		notes = mockk(relaxed = true)
		timeline = mockk(relaxed = true)
		tagIndex = mockk(relaxed = true)
		images = mockk(relaxed = true)
		coEvery { images.imageNames() } returns emptyMap()

		// Part One holds Chapter 1, which holds the first two scenes; a third scene is at the top level.
		every { scenes.getSceneTree() } returns tree(
			group(10, "Part One", group(11, "Chapter 1", scene(3, "The Fall"), scene(1, "The Garden"))),
			scene(2, "Tea Party"),
		)
		coEvery { tagIndex.builtIndex() } returns TagIndex(
			tagToEntities = mapOf(
				"pov" to setOf(TaggedEntityRef(TaggedEntityType.Scene, 1), TaggedEntityRef(TaggedEntityType.Encyclopedia, 7)),
			),
			countsByType = emptyMap(),
		)
		every { notes.notesListFlow } returns MutableStateFlow(
			listOf(
				NoteContainer(NoteContent(5, Clock.System.now(), "# Plans\n\nMore later", tags = setOf("draft"))),
				NoteContainer(NoteContent(4, Clock.System.now(), "   \n")),
			)
		)
		every { timeline.timelineFlow } returns MutableStateFlow(
			TimeLineContainer(
				listOf(
					TimeLineEvent(id = 9, order = 0, date = "1865", content = "Alice *falls*."),
					TimeLineEvent(id = 8, order = 1, content = "The trial\nof the Knave"),
				)
			)
		)
		val alice = EntryContent(7, "Alice", EntryType.PERSON, "", tags = setOf("#Lead"), aliases = listOf("Alys", " "))
		val rabbit = EntryContent(6, "White Rabbit", EntryType.PERSON, "", tags = emptySet())
		val wonderland = EntryContent(12, "wonderland", EntryType.PLACE, "", tags = setOf("pov"))
		// The entries on disk; the list the repository last emitted may be behind them.
		coEvery { encyclopedia.readEntryDefs() } returns listOf(rabbit, wonderland, alice).map { it.toDef(projectDef) }
		coEvery { encyclopedia.ensureEntriesLoaded() } returns listOf(rabbit, alice).map { it.toDef(projectDef) }
		every { encyclopedia.loadEntry(alice.toDef(projectDef)) } returns EntryContainer(alice)
		every { encyclopedia.loadEntry(wonderland.toDef(projectDef)) } returns EntryContainer(wonderland)
		every { encyclopedia.loadEntry(rabbit.toDef(projectDef)) } throws IllegalStateException("corrupt")
	}

	private fun catalog() = BuildEntityCatalogUseCase(encyclopedia, scenes, notes, timeline, tagIndex, images)

	private fun item(id: Int, type: SceneItem.Type, name: String) =
		SceneItem(projectDef = projectDef, type = type, id = id, name = name, order = id)

	private fun scene(id: Int, name: String) = TreeNode(item(id, SceneItem.Type.Scene, name))

	private fun group(id: Int, name: String, vararg children: TreeNode<SceneItem>) =
		TreeNode(item(id, SceneItem.Type.Group, name)).apply { children.forEach { addChild(it) } }

	private fun tree(vararg topLevel: TreeNode<SceneItem>) = Tree<SceneItem>().apply {
		setRoot(TreeNode(item(SceneItem.ROOT_ID, SceneItem.Type.Root, "")).apply { topLevel.forEach { addChild(it) } })
	}.toImmutableTree()

	@Test
	fun `scenes come in story order with their groups, and their tags from the index`() = runTest(mainTestDispatcher) {
		val items = catalog().items(setOf(CatalogKind.Scene))

		assertEquals(listOf("The Fall", "The Garden", "Tea Party"), items.map { it.name })
		assertEquals(listOf("Part One › Chapter 1", "Part One › Chapter 1", null), items.map { it.detail })
		assertEquals(listOf(emptySet(), setOf("pov"), emptySet()), items.map { it.tags })
	}

	@Test
	fun `entries come as they are on disk, by name with their aliases, types, and tags, and one that cannot be read by name alone`() = runTest(mainTestDispatcher) {
		val items = catalog().items(setOf(CatalogKind.Entry))

		assertEquals(listOf("Alice", "White Rabbit", "wonderland"), items.map { it.name })
		assertEquals(listOf(listOf("Alys"), emptyList(), emptyList()), items.map { it.aliases })
		assertEquals(listOf(EntryType.PERSON, EntryType.PERSON, EntryType.PLACE), items.map { it.entryType })
		assertEquals(listOf(setOf("Lead"), emptySet(), setOf("pov")), items.map { it.tags })
	}

	@Test
	fun `notes are named by their first line, and events by their date or first line`() = runTest(mainTestDispatcher) {
		val items = catalog().items(setOf(CatalogKind.Note, CatalogKind.Event))

		assertEquals(
			listOf(
				CatalogItem(CatalogKind.Note, 4, "(empty note)"),
				CatalogItem(CatalogKind.Note, 5, "Plans", tags = setOf("draft")),
				CatalogItem(CatalogKind.Event, 9, "1865", detail = "Alice falls."),
				CatalogItem(CatalogKind.Event, 8, "The trial"),
			),
			items,
		)
	}

	@Test
	fun `a note or event opening with an image is named by the image's current name`() = runTest(mainTestDispatcher) {
		coEvery { images.imageNames() } returns mapOf(3 to "Cheshire Cat")
		every { notes.notesListFlow } returns MutableStateFlow(
			listOf(
				NoteContainer(NoteContent(5, Clock.System.now(), "![image](hammer://image/3)\n\nGrin")),
				NoteContainer(NoteContent(4, Clock.System.now(), "![image](hammer://image/9)\n\nUnknown")),
				NoteContainer(NoteContent(6, Clock.System.now(), "![image](hammer://image/9)")),
			)
		)

		val items = catalog().items(setOf(CatalogKind.Note))

		assertEquals(listOf("(empty note)", "Cheshire Cat", "Unknown"), items.map { it.name })
	}

	@Test
	fun `only the kinds asked for are read`() = runTest(mainTestDispatcher) {
		assertEquals(setOf(CatalogKind.Event), catalog().items(setOf(CatalogKind.Event)).map { it.kind }.toSet())
		assertEquals(
			listOf(CatalogKind.Scene to 3, CatalogKind.Note to 2, CatalogKind.Entry to 3, CatalogKind.Event to 2),
			catalog().items().groupingBy { it.kind }.eachCount().toList(),
		)
	}

	private val items = listOf(
		CatalogItem(CatalogKind.Scene, 1, "The Garden", tags = setOf("pov")),
		CatalogItem(CatalogKind.Entry, 7, "Alice", aliases = listOf("Alys"), entryType = EntryType.PERSON, tags = setOf("Lead")),
		CatalogItem(CatalogKind.Entry, 12, "Wonderland", entryType = EntryType.PLACE),
		CatalogItem(CatalogKind.Entry, 6, "White Rabbit", entryType = EntryType.PERSON),
		CatalogItem(CatalogKind.Note, 5, "Plans about Alice"),
	)

	@Test
	fun `a query matches names and aliases ignoring case, keeping the order`() {
		fun names(query: String) = filterCatalog(items, query, CatalogFilter()).map { it.name }

		assertEquals(listOf("Alice", "Plans about Alice"), names("ALICE"))
		assertEquals(listOf("Alice"), names(" alys "))
		assertEquals(items.map { it.name }, names("  "))
		assertEquals(emptyList(), names("Cheshire"))
	}

	@Test
	fun `a filter narrows by kind, entry type, and any of its tags`() {
		fun names(filter: CatalogFilter) = filterCatalog(items, "", filter).map { it.name }

		assertEquals(listOf("Alice", "Wonderland", "White Rabbit"), names(CatalogFilter(kinds = setOf(CatalogKind.Entry))))
		assertEquals(listOf("Alice", "White Rabbit"), names(CatalogFilter(setOf(CatalogKind.Entry), entryTypes = setOf(EntryType.PERSON))))
		// Entry types say nothing about other kinds.
		assertEquals(listOf("The Garden", "Alice", "White Rabbit", "Plans about Alice"), names(CatalogFilter(entryTypes = setOf(EntryType.PERSON))))
		assertEquals(listOf("The Garden", "Alice"), names(CatalogFilter(tags = setOf("#LEAD", "pov"))))
		assertEquals(listOf("Alice"), names(CatalogFilter(setOf(CatalogKind.Entry), tags = setOf("lead", "pov"))))
	}

	@Test
	fun `an item is found by kind and id`() {
		assertEquals("Alice", items.find(CatalogRef(CatalogKind.Entry, 7))?.name)
		assertNull(items.find(CatalogRef(CatalogKind.Scene, 7)))
		assertEquals(CatalogKind.Event, CatalogKind.of("event"))
		assertNull(CatalogKind.of("timeline"))
	}
}
