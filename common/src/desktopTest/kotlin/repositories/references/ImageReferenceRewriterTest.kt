package repositories.references

import ENCYCLOPEDIA_ONLY_PROJECT_NAME
import com.darkrockstudios.apps.hammer.base.http.EntityType
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.SceneBuffer
import com.darkrockstudios.apps.hammer.common.data.SceneContent
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.UpdateSource
import com.darkrockstudios.apps.hammer.common.data.drafts.DraftDef
import com.darkrockstudios.apps.hammer.common.data.drafts.SceneDraftRepository
import com.darkrockstudios.apps.hammer.common.data.drafts.SceneDraftsDatasource
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaDatasource
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaRepository
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContainer
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContent
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.data.id.IdAllocator
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NotesRepository
import com.darkrockstudios.apps.hammer.common.data.notesrepository.note.NoteContainer
import com.darkrockstudios.apps.hammer.common.data.notesrepository.note.NoteContent
import com.darkrockstudios.apps.hammer.common.data.references.ImageReferenceRewriter
import com.darkrockstudios.apps.hammer.common.data.references.TouchedEntity
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorService
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncJournal
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineContainer
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEvent
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineRepository
import com.darkrockstudios.apps.hammer.common.data.tree.ImmutableTree
import com.darkrockstudios.apps.hammer.common.data.tree.Tree
import com.darkrockstudios.apps.hammer.common.data.tree.TreeNode
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import createProject
import getProjectDef
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import repositories.encyclopedia.entry1
import repositories.encyclopedia.entry2
import utils.BaseTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ImageReferenceRewriterTest : BaseTest() {

	private val projectDef = getProjectDef(ENCYCLOPEDIA_ONLY_PROJECT_NAME)

	private lateinit var ffs: FakeFileSystem
	private lateinit var syncJournal: SyncJournal
	private lateinit var datasource: EncyclopediaDatasource
	private lateinit var repository: EncyclopediaRepository
	private lateinit var sceneEditor: SceneEditorService
	private lateinit var draftsDatasource: SceneDraftsDatasource
	private lateinit var draftRepository: SceneDraftRepository
	private lateinit var notesRepository: NotesRepository
	private lateinit var timeLineRepository: TimeLineRepository

	private var activeScenes: List<SceneItem> = emptyList()
	private var archivedScenes: List<SceneItem> = emptyList()
	private val sceneFiles = mutableMapOf<Int, String>()
	private val sceneBuffers = mutableMapOf<Int, String>()
	private val unrestoredEdits = mutableMapOf<Int, String>()
	private var notes: List<NoteContent> = emptyList()
	private var events: List<TimeLineEvent> = emptyList()

	@BeforeEach
	override fun setup() {
		super.setup()
		ffs = FakeFileSystem()
		createProject(ffs, ENCYCLOPEDIA_ONLY_PROJECT_NAME)
		syncJournal = mockk(relaxUnitFun = true)
		every { syncJournal.isServerSynchronized() } returns true
		coEvery { syncJournal.isEntityDirty(any()) } returns false

		sceneEditor = mockk(relaxed = true)
		notesRepository = mockk(relaxed = true)
		timeLineRepository = mockk(relaxed = true)
		stubScenes()
		coEvery { notesRepository.loadNotesImperative() } answers { notes.map(::NoteContainer) }
		coEvery { timeLineRepository.loadTimeline() } answers { TimeLineContainer(events) }
		coEvery { timeLineRepository.updateEvent(any(), any()) } returns true

		setupKoin(module {
			single { syncJournal }
			single { mockk<IdAllocator>() }
			scope<ProjectDefScope> {
				scoped<ProjectDef> { projectDef }
				scoped { repository }
				scoped { sceneEditor }
				scoped { draftRepository }
				scoped { notesRepository }
				scoped { timeLineRepository }
			}
		})
		datasource = EncyclopediaDatasource(projectDef, createTomlSerializer(), ffs)
		repository = EncyclopediaRepository(projectDef, mockk<IdAllocator>(), datasource, syncJournal)
		draftsDatasource = SceneDraftsDatasource(projectDef, ffs)
		draftRepository = SceneDraftRepository(projectDef, draftsDatasource, mockk<Clock>())
	}

	/** The scene service over in-memory scene files, buffers and unrestored edits. */
	private fun stubScenes() {
		every { sceneEditor.getSceneTree() } answers { sceneTree(activeScenes) }
		every { sceneEditor.getArchivedScenes() } answers { archivedScenes }
		every { sceneEditor.getSceneItemFromIdIncludingArchived(any()) } answers {
			(activeScenes + archivedScenes).find { it.id == firstArg() }
		}
		every { sceneEditor.getSceneBuffer(any()) } answers {
			val scene = firstArg<SceneItem>()
			sceneBuffers[scene.id]?.let { SceneBuffer(SceneContent(scene, it), dirty = true, source = UpdateSource.Editor) }
		}
		every { sceneEditor.getCurrentSceneContentOrNull(any()) } answers {
			val id = firstArg<SceneItem>().id
			sceneBuffers[id] ?: sceneFiles[id]
		}
		every { sceneEditor.resolveScenePathFromFilesystemIncludingArchived(any()) } answers {
			val id = firstArg<Int>()
			if (id in sceneFiles) scenePath(id) else null
		}
		every { sceneEditor.loadSceneMarkdownRaw(any(), any()) } answers {
			sceneFiles.getValue(firstArg<SceneItem>().id)
		}
		coEvery { sceneEditor.storeSceneMarkdownRaw(any(), any()) } answers {
			val content = firstArg<SceneContent>()
			assertEquals(scenePath(content.scene.id), secondArg<HPath?>())
			sceneFiles[content.scene.id] = content.markdown!!
			true
		}
		coEvery { sceneEditor.rewriteSceneText(any(), any()) } answers {
			val id = firstArg<SceneItem>().id
			val current = sceneBuffers[id]
			val updated = current?.let(secondArg<(String) -> String>())
			if (updated != null && updated != current) {
				sceneBuffers[id] = updated
				true
			} else {
				false
			}
		}
		every { sceneEditor.hasUnrestoredEdits(any()) } answers { firstArg<SceneItem>().id in unrestoredEdits }
		every { sceneEditor.loadUnrestoredEdits(any()) } answers { unrestoredEdits[firstArg<SceneItem>().id] }
		every { sceneEditor.updateUnrestoredEdits(any(), any()) } answers {
			val id = firstArg<SceneItem>().id
			val current = unrestoredEdits[id]
			val updated = current?.let(secondArg<(String) -> String>())
			if (updated != null && updated != current) {
				unrestoredEdits[id] = updated
				true
			} else {
				false
			}
		}
	}

	private fun scenePath(id: Int) = HPath("/scenes/$id.md", "$id.md", true)

	private fun scene(id: Int, archived: Boolean = false) = SceneItem(
		projectDef = projectDef,
		type = SceneItem.Type.Scene,
		id = id,
		name = "Scene $id",
		order = id,
		archived = archived,
	)

	private fun sceneTree(scenes: List<SceneItem>): ImmutableTree<SceneItem> {
		val root = TreeNode(SceneItem(projectDef, SceneItem.Type.Root, SceneItem.ROOT_ID, "Root", 0))
		scenes.forEach { root.addChild(TreeNode(it)) }
		return Tree<SceneItem>().apply { setRoot(root) }.toImmutableTree()
	}

	private fun note(id: Int, content: String) = NoteContent(id, Instant.fromEpochSeconds(0), content)

	private fun event(id: Int, content: String) = TimeLineEvent(id = id, order = id, content = content)

	private fun draft(id: Int, sceneId: Int, content: String): DraftDef {
		val def = DraftDef(id, sceneId, Instant.fromEpochSeconds(1_700_000_000L + id), "Draft $id")
		draftsDatasource.storeDraft(def, content)
		return def
	}

	private suspend fun store(entry: EntryContent) {
		datasource.createEntry(EntryContainer(entry))
	}

	private fun entry(id: Int): EntryContent = repository.loadEntry(id).entry

	private fun touched(vararg ids: Int) = ids.map { TouchedEntity(EntityType.EncyclopediaEntry, it) }.toSet()

	@Test
	fun `remap rewrites entry text and image lists and reports the entries it touched`() = runTest {
		store(entry1().copy(text = "Portrait:\n\n![Alice](hammer://image/5)", images = listOf(5, 6)))
		store(entry2().copy(images = listOf(6, 5)))

		val result = ImageReferenceRewriter(projectDef).remap(mapOf(5 to 50))

		assertEquals(touched(1, 2), result)
		assertEquals("Portrait:\n\n![Alice](hammer://image/50)", entry(1).text)
		assertEquals(listOf(50, 6), entry(1).images)
		assertEquals(listOf(6, 50), entry(2).images)
		coVerify { syncJournal.markEntityAsDirty(1) }
		coVerify { syncJournal.markEntityAsDirty(2) }
	}

	@Test
	fun `entries that do not refer to the image are not rewritten`() = runTest {
		store(entry1().copy(images = listOf(5)))
		val untouchedPath = datasource.getEntryPath(entry2().toDef(projectDef)).toOkioPath()
		val before = ffs.read(untouchedPath) { readUtf8() }

		val result = ImageReferenceRewriter(projectDef).remap(mapOf(5 to 50))

		assertEquals(touched(1), result)
		assertEquals(before, ffs.read(untouchedPath) { readUtf8() })
		coVerify(exactly = 0) { syncJournal.markEntityAsDirty(2) }
	}

	@Test
	fun `removeAll removes the image from text and lists`() = runTest {
		store(entry1().copy(text = "Top\n\n![a](hammer://image/5)\n\nBottom", images = listOf(6, 5)))
		store(entry2().copy(images = listOf(5)))

		val result = ImageReferenceRewriter(projectDef).removeAll(5)

		assertEquals(touched(1, 2), result)
		assertEquals("Top\n\nBottom", entry(1).text)
		assertEquals(listOf(6), entry(1).images)
		assertTrue(entry(2).images.isEmpty())
	}

	@Test
	fun `findUsages lists the entries that show or embed the image`() = runTest {
		store(entry1().copy(text = "![a](hammer://image/5)"))
		store(entry2().copy(images = listOf(5)))

		val usages = ImageReferenceRewriter(projectDef).findUsages(5)

		assertEquals(touched(1, 2), usages.usages.map { it.entity }.toSet())
		assertEquals(setOf("Entry 1", "Entry 2"), usages.usages.map { it.name }.toSet())
		assertTrue(ImageReferenceRewriter(projectDef).findUsages(99).isUnused)
	}

	@Test
	fun `isUsed finds an image that is shown or embedded`() = runTest {
		store(entry1().copy(text = "![a](hammer://image/7)"))
		store(entry2().copy(images = listOf(5)))

		val rewriter = ImageReferenceRewriter(projectDef)

		assertTrue(rewriter.isUsed(5))
		assertTrue(rewriter.isUsed(7))
		assertFalse(rewriter.isUsed(99))
	}

	@Test
	fun `findReferencedIds collects every image entries show or embed`() = runTest {
		store(entry1().copy(text = "![a](hammer://image/7)\n\n![b](hammer://image/5)", images = listOf(5)))
		store(entry2().copy(images = listOf(6, 5)))

		assertEquals(setOf(5, 6, 7), ImageReferenceRewriter(projectDef).findReferencedIds())
	}

	@Test
	fun `an unreadable entry does not stop the others`() = runTest {
		val brokenDir = EncyclopediaDatasource.getTypeDirectory(projectDef, EntryType.PERSON, ffs).toOkioPath()
		ffs.write(brokenDir / "person~3~Broken.toml") { writeUtf8("this is [[ not toml") }
		store(entry2().copy(images = listOf(5)))

		val rewriter = ImageReferenceRewriter(projectDef)

		assertEquals(touched(2), rewriter.remap(mapOf(5 to 50)))
		assertEquals(listOf(50), entry(2).images)
		assertEquals(touched(2), rewriter.findUsages(50).usages.map { it.entity }.toSet())
	}

	@Test
	fun `an entry that fails validation is still rewritten`() = runTest {
		val tooLong = "x".repeat(EncyclopediaRepository.MAX_TAG_SIZE + 1)
		store(entry1().copy(tags = setOf(tooLong), images = listOf(5)))

		assertEquals(touched(1), ImageReferenceRewriter(projectDef).remap(mapOf(5 to 50)))
		assertEquals(listOf(50), entry(1).images)
		assertEquals(setOf(tooLong), entry(1).tags)
	}

	@Test
	fun `remap with no real changes touches nothing`() = runTest {
		store(entry1().copy(images = listOf(5)))

		assertTrue(ImageReferenceRewriter(projectDef).remap(mapOf(5 to 5)).isEmpty())
		assertTrue(ImageReferenceRewriter(projectDef).remap(emptyMap()).isEmpty())
		coVerify(exactly = 0) { syncJournal.markEntityAsDirty(any()) }
	}

	// region Markdown holders

	private fun touched(type: EntityType, vararg ids: Int) = ids.map { TouchedEntity(type, it) }.toSet()

	@Test
	fun `a scene with a loaded buffer is rewritten through the buffer, never its file`() = runTest {
		activeScenes = listOf(scene(1))
		sceneFiles[1] = "Saved ![a](hammer://image/5)"
		sceneBuffers[1] = "Unsaved\n\n![a](hammer://image/5)"

		val result = ImageReferenceRewriter(projectDef).remap(mapOf(5 to 50))

		assertEquals(touched(EntityType.Scene, 1), result)
		assertEquals("Unsaved\n\n![a](hammer://image/50)", sceneBuffers[1])
		coVerify(exactly = 0) { sceneEditor.replaceSceneText(any(), any()) }
		coVerify(exactly = 0) { sceneEditor.storeSceneMarkdownRaw(any(), any()) }
		assertEquals("Saved ![a](hammer://image/5)", sceneFiles[1])
	}

	@Test
	fun `a scene without a buffer is rewritten in its file`() = runTest {
		activeScenes = listOf(scene(1))
		sceneFiles[1] = "![a](hammer://image/5)\n\nBody"

		val result = ImageReferenceRewriter(projectDef).remap(mapOf(5 to 50))

		assertEquals(touched(EntityType.Scene, 1), result)
		assertEquals("![a](hammer://image/50)\n\nBody", sceneFiles[1])
		coVerify { sceneEditor.storeSceneMarkdownRaw(SceneContent(scene(1), "![a](hammer://image/50)\n\nBody"), scenePath(1)) }
		coVerify(exactly = 0) { sceneEditor.rewriteSceneText(any(), any()) }
	}

	@Test
	fun `an archived scene is rewritten in its file`() = runTest {
		archivedScenes = listOf(scene(4, archived = true))
		sceneFiles[4] = "Old ![a](hammer://image/5)"

		val result = ImageReferenceRewriter(projectDef).remap(mapOf(5 to 50))

		assertEquals(touched(EntityType.Scene, 4), result)
		assertEquals("Old ![a](hammer://image/50)", sceneFiles[4])
	}

	@Test
	fun `unrestored edits are rewritten so restoring them keeps the new reference, but not reported for sync`() = runTest {
		activeScenes = listOf(scene(1))
		sceneFiles[1] = "Saved, no image"
		unrestoredEdits[1] = "Typed later\n\n![a](hammer://image/5)"

		val result = ImageReferenceRewriter(projectDef).remap(mapOf(5 to 50))

		assertTrue(result.isEmpty())
		assertEquals("Typed later\n\n![a](hammer://image/50)", unrestoredEdits[1])
		assertEquals("Saved, no image", sceneFiles[1])
		coVerify(exactly = 0) { sceneEditor.storeSceneMarkdownRaw(any(), any()) }
	}

	@Test
	fun `a draft is rewritten on disk and marked dirty`() = runTest {
		activeScenes = listOf(scene(1))
		sceneFiles[1] = "Scene text"
		val def = draft(20, sceneId = 1, content = "Draft ![a](hammer://image/5)")

		val result = ImageReferenceRewriter(projectDef).remap(mapOf(5 to 50))

		assertEquals(touched(EntityType.SceneDraft, 20), result)
		assertEquals("Draft ![a](hammer://image/50)", draftsDatasource.loadDraftContent(def))
		coVerify { syncJournal.markEntityAsDirty(20) }
	}

	@Test
	fun `a note and an event are rewritten through their repositories`() = runTest {
		notes = listOf(note(30, "Note ![a](hammer://image/5)"), note(31, "Other note"))
		events = listOf(event(40, "Event ![a](hammer://image/5)"), event(41, "Other event"))

		val result = ImageReferenceRewriter(projectDef).remap(mapOf(5 to 50))

		assertEquals(touched(EntityType.Note, 30) + touched(EntityType.TimelineEvent, 40), result)
		coVerify { notesRepository.updateNote(note(30, "Note ![a](hammer://image/50)"), true) }
		coVerify { timeLineRepository.updateEvent(event(40, "Event ![a](hammer://image/50)"), true) }
		coVerify(exactly = 1) { notesRepository.updateNote(any(), any()) }
		coVerify(exactly = 1) { timeLineRepository.updateEvent(any(), any()) }
	}

	@Test
	fun `holders that do not use the image are not written`() = runTest {
		activeScenes = listOf(scene(1), scene(2))
		sceneFiles[1] = "Plain"
		sceneFiles[2] = "![b](hammer://image/6)"
		sceneBuffers[2] = "![b](hammer://image/6)"
		archivedScenes = listOf(scene(3, archived = true))
		sceneFiles[3] = "Archived"
		val def = draft(20, sceneId = 1, content = "Draft")
		notes = listOf(note(30, "Note"))
		events = listOf(event(40, "Event"))

		val result = ImageReferenceRewriter(projectDef).remap(mapOf(5 to 50))

		assertTrue(result.isEmpty())
		coVerify(exactly = 0) { sceneEditor.storeSceneMarkdownRaw(any(), any()) }
		assertEquals("![b](hammer://image/6)", sceneBuffers[2])
		coVerify(exactly = 0) { notesRepository.updateNote(any(), any()) }
		coVerify(exactly = 0) { timeLineRepository.updateEvent(any(), any()) }
		coVerify(exactly = 0) { syncJournal.markEntityAsDirty(any()) }
		assertEquals("Draft", draftsDatasource.loadDraftContent(def))
	}

	@Test
	fun `a failing holder does not stop the others`() = runTest {
		activeScenes = listOf(scene(1))
		sceneFiles[1] = "![a](hammer://image/5)"
		coEvery { notesRepository.loadNotesImperative() } throws IllegalStateException("broken notes")
		coEvery { sceneEditor.storeSceneMarkdownRaw(any(), any()) } throws IllegalStateException("broken disk")
		events = listOf(event(40, "![a](hammer://image/5)"))
		store(entry1().copy(images = listOf(5)))

		val result = ImageReferenceRewriter(projectDef).remap(mapOf(5 to 50))

		assertEquals(
			touched(EntityType.TimelineEvent, 40) + touched(EntityType.EncyclopediaEntry, 1),
			result,
		)
	}

	@Test
	fun `findUsages names every kind of holder`() = runTest {
		activeScenes = listOf(scene(1), scene(2))
		sceneFiles[1] = "![a](hammer://image/5)"
		sceneFiles[2] = "Saved"
		unrestoredEdits[2] = "![a](hammer://image/5)"
		archivedScenes = listOf(scene(3, archived = true))
		sceneFiles[3] = "![a](hammer://image/5)"
		draft(20, sceneId = 1, content = "![a](hammer://image/5)")
		notes = listOf(note(30, "A note about the cat\n\n![a](hammer://image/5)"))
		events = listOf(event(40, "${"Long ".repeat(10)}event\n\n![a](hammer://image/5)"))

		val usages = ImageReferenceRewriter(projectDef).findUsages(5).usages

		assertEquals(
			mapOf(
				TouchedEntity(EntityType.Scene, 1) to "Scene 1",
				TouchedEntity(EntityType.Scene, 2) to "Scene 2",
				TouchedEntity(EntityType.Scene, 3) to "Scene 3",
				TouchedEntity(EntityType.SceneDraft, 20) to "Scene 1: Draft 20",
				TouchedEntity(EntityType.Note, 30) to "A note about the cat",
				TouchedEntity(EntityType.TimelineEvent, 40) to "Long Long Long Long Long Long Long Long…",
			),
			usages.associate { it.entity to it.name },
		)
		assertEquals(usages.size, usages.map { it.entity }.toSet().size)
	}

	@Test
	fun `removeAll takes the image out of every holder`() = runTest {
		activeScenes = listOf(scene(1), scene(2))
		sceneFiles[1] = "Top\n\n![a](hammer://image/5)\n\nBottom"
		sceneFiles[2] = "Saved"
		sceneBuffers[2] = "Live ![a](hammer://image/5) text"
		unrestoredEdits[1] = "![a](hammer://image/5)\n\nEdited"
		archivedScenes = listOf(scene(3, archived = true))
		sceneFiles[3] = "![a](hammer://image/5)"
		val def = draft(20, sceneId = 1, content = "Draft\n\n![a](hammer://image/5)")
		notes = listOf(note(30, "Note\n\n![a](hammer://image/5)"))
		events = listOf(event(40, "![a](hammer://image/5)\n\nEvent"))
		store(entry1().copy(text = "![a](hammer://image/5)", images = listOf(5, 6)))

		val result = ImageReferenceRewriter(projectDef).removeAll(5)

		assertEquals(
			touched(EntityType.Scene, 1, 2, 3) + touched(EntityType.SceneDraft, 20) +
				touched(EntityType.Note, 30) + touched(EntityType.TimelineEvent, 40) +
				touched(EntityType.EncyclopediaEntry, 1),
			result,
		)
		assertEquals("Top\n\nBottom", sceneFiles[1])
		assertEquals("Live  text", sceneBuffers[2])
		assertEquals("Edited", unrestoredEdits[1])
		assertEquals("", sceneFiles[3])
		assertEquals("Draft\n", draftsDatasource.loadDraftContent(def))
		coVerify { notesRepository.updateNote(note(30, "Note\n"), true) }
		coVerify { timeLineRepository.updateEvent(event(40, "Event"), true) }
		assertEquals(listOf(6), entry(1).images)
		assertEquals("", entry(1).text)
	}

	@Test
	fun `isUsed stops at the first holder that uses the image`() = runTest {
		store(entry1().copy(images = listOf(5)))
		activeScenes = listOf(scene(1))
		sceneFiles[1] = "![a](hammer://image/7)"

		val rewriter = ImageReferenceRewriter(projectDef)

		assertTrue(rewriter.isUsed(5))
		verify(exactly = 0) { sceneEditor.getSceneTree() }
		assertTrue(rewriter.isUsed(7))
		assertFalse(rewriter.isUsed(99))
	}

	@Test
	fun `findReferencedIds covers every holder`() = runTest {
		activeScenes = listOf(scene(1))
		sceneFiles[1] = "![a](hammer://image/1)"
		unrestoredEdits[1] = "![a](hammer://image/2)"
		archivedScenes = listOf(scene(3, archived = true))
		sceneFiles[3] = "![a](hammer://image/3)"
		draft(20, sceneId = 1, content = "![a](hammer://image/4)")
		notes = listOf(note(30, "![a](hammer://image/5)"))
		events = listOf(event(40, "![a](hammer://image/6)"))

		assertEquals(setOf(1, 2, 3, 4, 5, 6), ImageReferenceRewriter(projectDef).findReferencedIds())
	}

	// endregion
}
