package integration

import PROJECT_2_NAME
import com.darkrockstudios.apps.hammer.common.data.ClientResult
import com.darkrockstudios.apps.hammer.common.data.SceneContent
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.UpdateSource
import com.darkrockstudios.apps.hammer.common.data.drafts.SceneDraftRepository
import com.darkrockstudios.apps.hammer.common.data.drafts.SceneDraftsDatasource
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaDatasource
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaRepository
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaService
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EntryError
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContent
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.rename.*
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NotesDatasource
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NotesRepository
import com.darkrockstudios.apps.hammer.common.data.projectbackup.ProjectBackupRepository
import com.darkrockstudios.apps.hammer.common.data.references.BackfillEntryReferencesUseCase
import com.darkrockstudios.apps.hammer.common.data.references.WholeWordCaseSensitiveMatcher
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineDatasource
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

class RenameEntryUseCaseTest : BaseIntegrationTest() {

	private lateinit var encyclopediaRepository: EncyclopediaRepository
	private lateinit var encyclopediaService: EncyclopediaService
	private lateinit var notesRepository: NotesRepository
	private lateinit var timeLineRepository: TimeLineRepository
	private lateinit var drafts: SceneDraftRepository
	private lateinit var backupRepository: ProjectBackupRepository
	private lateinit var backfill: BackfillEntryReferencesUseCase
	private lateinit var useCase: RenameEntryUseCase

	private lateinit var robert: EntryContent
	private lateinit var martha: EntryContent
	private var noteId = 0
	private var eventId = 0

	@BeforeEach
	override fun setup() {
		super.setup()
		setupKoin(module {
			single { idAllocator }
			single { syncJournal }
		})
	}

	private suspend fun TestScope.createStack() {
		configureProject(PROJECT_2_NAME)
		sceneEditorService.initialize()

		encyclopediaRepository = EncyclopediaRepository(
			projectDef = projectDef,
			idAllocator = idAllocator,
			datasource = EncyclopediaDatasource(projectDef, toml, ffs, mockk(relaxed = true)),
			syncJournal = syncJournal,
		)
		encyclopediaService = EncyclopediaService(encyclopediaRepository, statisticsRepository, referenceIndexRepository)
		notesRepository = NotesRepository(projectDef, idAllocator, syncJournal, NotesDatasource(projectDef, ffs, toml))
		timeLineRepository = TimeLineRepository(projectDef, idAllocator, TimeLineDatasource(ffs, toml)).initialize()
		drafts = SceneDraftRepository(
			projectDef,
			sceneContentRepository,
			SceneDraftsDatasource(ffs, sceneDatasource),
			Clock.System,
		)
		backupRepository = mockk()
		every { backupRepository.supportsBackup() } returns true
		coEvery { backupRepository.createBackup(any()) } returns mockk()
		backfill = mockk(relaxed = true)
		advanceUntilIdle()

		useCase = RenameEntryUseCase(
			projectDef = projectDef,
			encyclopediaRepository = encyclopediaRepository,
			encyclopediaService = encyclopediaService,
			sceneEditor = sceneEditorService,
			sceneContentRepository = sceneContentRepository,
			drafts = drafts,
			notesRepository = notesRepository,
			timeLineRepository = timeLineRepository,
			backupRepository = backupRepository,
			backfillEntryReferences = backfill,
			matcher = WholeWordCaseSensitiveMatcher(),
		)

		robert = encyclopediaService.createEntry(
			name = "Robert Tallow",
			type = EntryType.PERSON,
			text = "Robert Tallow makes candles.",
			tags = emptySet(),
			imagePath = null,
			aliases = listOf("Bob", "Mr Tallow", "Tally"),
		).instance!!.entry
		martha = encyclopediaService.createEntry(
			name = "Martha Tallow",
			type = EntryType.PERSON,
			text = "Wife of Robert Tallow.",
			tags = emptySet(),
			imagePath = null,
		).instance!!.entry

		writeScene(1, "Robert Tallow walked in. Bob waved. ROBERT TALLOW! Martha Tallow smiled. Tallow's hat. The Tallows left. Tally too.")
		writeScene(6, "Robert Tallow again.")
		sceneEditorService.storeMetadata(
			sceneEditorService.loadSceneMetadata(6).copy(dismissedReferences = setOf(robert.id)),
			6,
		)
		sceneEditorService.storeMetadata(sceneEditorService.loadSceneMetadata(7).copy(outline = "Meet Mr Tallow"), 7)
		sceneEditorService.renameScene(scene(3), "Robert Tallow Day")
		writeScene(4, "Robert Tallow left town.")
		sceneEditorService.archiveScene(scene(4))

		noteId = (notesRepository.createNote("Robert Tallow owes money.") as ClientResult.Success).data.id
		eventId = timeLineRepository.createEvent("Robert Tallow is born.", date = "Year 1").id
		advanceUntilIdle()
	}

	private fun scene(id: Int): SceneItem = sceneEditorService.getSceneItemFromId(id)!!

	private suspend fun writeScene(id: Int, text: String) {
		sceneEditorService.storeSceneMarkdownRaw(SceneContent(scene(id), text))
	}

	private fun sceneText(id: Int): String = sceneEditorService.loadSceneMarkdownRaw(scene(id))

	private fun archivedText(id: Int): String {
		val item = sceneEditorService.getSceneItemFromIdIncludingArchived(id)!!
		return sceneEditorService.loadSceneMarkdownRaw(item, sceneEditorService.resolveScenePathFromFilesystemIncludingArchived(id))
	}

	private fun request(
		name: String = "Robert Tolliver",
		bob: FormAction = FormAction.Keep,
		mrTallow: FormAction = FormAction.Rename("Mr Tolliver"),
		tally: FormAction = FormAction.Remove,
		keepOld: Boolean = false,
	) = RenameRequest(
		entryId = robert.id,
		name = FormMapping("Robert Tallow", FormAction.Rename(name)),
		aliases = listOf(
			FormMapping("Bob", bob),
			FormMapping("Mr Tallow", mrTallow),
			FormMapping("Tally", tally),
		),
		keepOldNamesAsAliases = keepOld,
	)

	private fun renameDrafts(sceneId: Int) =
		drafts.findDrafts(sceneId).filter { it.draftName == "Before renaming Robert Tallow" }

	private fun RenamePreview.item(place: RenamePlace, id: Int): RenameItem =
		items.single { it.key == RenameItemKey(place, id) }

	private suspend fun applyDefaults(
		preview: RenamePreview,
		excluded: Set<RenameItemKey> = emptySet(),
		included: Set<RenameMatchKey> = emptySet(),
	) = useCase.apply(preview, excluded, included, draftName = "Before renaming Robert Tallow")

	@Test
	fun `Preview finds sure and unsure matches in every place`() = runTest(mainTestDispatcher) {
		createStack()

		val preview = useCase.preview(request())

		val scene1 = preview.item(RenamePlace.SCENE_TEXT, 1)
		assertEquals(listOf("Robert Tolliver", "ROBERT TOLLIVER"), scene1.matches.filter { it.isSure }.map { it.replacement })
		assertEquals(
			listOf(
				"Tallow" to UnsureReason.PartOfName("Tallow"),
				"Tallows" to UnsureReason.PartOfName("Tallow"),
			),
			scene1.unsure.map { it.key.matchedText to it.reason },
		)
		assertEquals("Tollivers", scene1.unsure[1].replacement)

		assertEquals(UnsureReason.Dismissed, preview.item(RenamePlace.SCENE_TEXT, 6).matches.single().reason)
		assertEquals(1, preview.item(RenamePlace.SCENE_TITLE, 3).sureCount)
		assertEquals("Mr Tolliver", preview.item(RenamePlace.SCENE_OUTLINE, 7).matches.single().replacement)
		assertEquals(1, preview.item(RenamePlace.ARCHIVED_SCENE_TEXT, 4).sureCount)
		assertEquals(1, preview.item(RenamePlace.NOTE, noteId).sureCount)
		assertEquals(1, preview.item(RenamePlace.TIMELINE_EVENT, eventId).sureCount)
		assertEquals(1, preview.item(RenamePlace.ENCYCLOPEDIA_ENTRY, martha.id).sureCount)
		assertEquals(1, preview.item(RenamePlace.ENCYCLOPEDIA_ENTRY, robert.id).sureCount)
		assertEquals(listOf("Bob", "Mr Tolliver"), preview.newAliases)
	}

	@Test
	fun `Apply rewrites sure matches everywhere and renames the entry last`() = runTest(mainTestDispatcher) {
		createStack()
		val preview = useCase.preview(request())

		val result = applyDefaults(preview)
		advanceUntilIdle()

		assertEquals(EntryError.NONE, result.entryError)
		assertTrue(result.backupMade)
		assertEquals(
			"Robert Tolliver walked in. Bob waved. ROBERT TOLLIVER! Martha Tallow smiled. Tallow's hat. The Tallows left. Tally too.",
			sceneText(1),
		)
		assertEquals("Robert Tallow again.", sceneText(6))
		assertEquals("Robert Tolliver Day", scene(3).name)
		assertEquals("Meet Mr Tolliver", sceneEditorService.loadSceneMetadata(7).outline)
		assertEquals("Robert Tolliver left town.", archivedText(4))
		assertEquals("Robert Tolliver owes money.", notesRepository.notesListFlow.first().single { it.note.id == noteId }.note.content)
		assertEquals("Robert Tolliver is born.", timeLineRepository.timelineFlow.first().events.single { it.id == eventId }.content)
		assertEquals("Wife of Robert Tolliver.", encyclopediaRepository.loadEntry(martha.id).entry.text)

		val renamed = encyclopediaRepository.loadEntry(robert.id).entry
		assertEquals("Robert Tolliver", renamed.name)
		assertEquals("Robert Tolliver makes candles.", renamed.text)
		assertEquals(listOf("Bob", "Mr Tolliver"), renamed.aliases)
		assertEquals("Robert Tolliver", result.newEntryDef?.name)
		assertEquals(9, result.replaced)
		assertEquals(0, result.skippedStale)
		coVerify { backfill(match { it.name == "Robert Tolliver" }) }
		assertTrue(encyclopediaRepository.entryListFlow.first().any { it.name == "Robert Tolliver" })
	}

	@Test
	fun `Apply keeps each changed scene's old text as a draft`() = runTest(mainTestDispatcher) {
		createStack()
		val preview = useCase.preview(request())

		applyDefaults(preview)

		assertTrue(drafts.loadDraftContent(renameDrafts(1).single())!!.startsWith("Robert Tallow walked in."))
		assertEquals(1, renameDrafts(4).size)
		assertTrue(renameDrafts(6).isEmpty())
	}

	@Test
	fun `Included unsure matches are applied and excluded items are left alone`() = runTest(mainTestDispatcher) {
		createStack()
		val preview = useCase.preview(request())
		val unsure = preview.item(RenamePlace.SCENE_TEXT, 1).unsure.map { it.key }.toSet() +
			preview.item(RenamePlace.SCENE_TEXT, 6).unsure.map { it.key }

		applyDefaults(preview, excluded = setOf(RenameItemKey(RenamePlace.NOTE, noteId)), included = unsure)

		assertEquals(
			"Robert Tolliver walked in. Bob waved. ROBERT TOLLIVER! Martha Tallow smiled. Tolliver's hat. The Tollivers left. Tally too.",
			sceneText(1),
		)
		assertEquals("Robert Tolliver again.", sceneText(6))
		assertEquals("Robert Tallow owes money.", notesRepository.notesListFlow.first().single { it.note.id == noteId }.note.content)
	}

	@Test
	fun `Text changed since the preview is skipped`() = runTest(mainTestDispatcher) {
		createStack()
		val preview = useCase.preview(request())
		writeScene(1, "Then Robert Tallow walked in. ROBERT TALLOW!")

		val result = applyDefaults(preview)

		assertEquals("Then Robert Tallow walked in. ROBERT TALLOW!", sceneText(1))
		assertEquals(2, result.skippedStale)
		assertEquals(7, result.replaced)
	}

	@Test
	fun `A scene with unsaved edits is renamed from its buffer`() = runTest(mainTestDispatcher) {
		createStack()
		sceneContentRepository.onContentChanged(SceneContent(scene(6), "Unsaved: Robert Tallow."), UpdateSource.Editor)
		advanceUntilIdle()
		sceneEditorService.storeMetadata(sceneEditorService.loadSceneMetadata(6).copy(dismissedReferences = emptySet()), 6)

		val preview = useCase.preview(request())
		assertEquals(1, preview.item(RenamePlace.SCENE_TEXT, 6).sureCount)
		applyDefaults(preview)
		advanceUntilIdle()

		assertEquals("Unsaved: Robert Tolliver.", sceneText(6))
		assertEquals("Unsaved: Robert Tolliver.", sceneContentRepository.getSceneBuffer(scene(6))?.content?.markdown)
		assertFalse(sceneEditorService.hasDirtyBuffer(6))
		assertEquals("Unsaved: Robert Tallow.", drafts.loadDraftContent(renameDrafts(6).single()))
	}

	@Test
	fun `A failed backup changes nothing`() = runTest(mainTestDispatcher) {
		createStack()
		coEvery { backupRepository.createBackup(any()) } returns null
		val preview = useCase.preview(request())

		val result = applyDefaults(preview)

		assertTrue(result.backupFailed)
		assertTrue(sceneText(1).startsWith("Robert Tallow walked in."))
		assertEquals("Robert Tallow", encyclopediaRepository.loadEntry(robert.id).entry.name)
	}

	@Test
	fun `Merging an alias into the name and keeping old names`() = runTest(mainTestDispatcher) {
		createStack()
		val context = useCase.loadContext(robert.id)

		val merged = useCase.check(context, request(bob = FormAction.Rename("Robert Tolliver")))
		assertEquals(listOf("Mr Tolliver"), merged.newAliases)

		val kept = useCase.check(context, request(keepOld = true))
		assertEquals(listOf("Bob", "Mr Tolliver", "Robert Tallow", "Mr Tallow", "Tally"), kept.newAliases)
	}

	@Test
	fun `Check reports collisions, blank forms and no-op requests`() = runTest(mainTestDispatcher) {
		createStack()
		val context = useCase.loadContext(robert.id)

		val collision = useCase.check(context, request(name = "Martha Tallow"))
		assertEquals(listOf(FormCollision("Martha Tallow", "Martha Tallow")), collision.collisions)
		assertTrue(collision.canProceed)

		val blank = useCase.check(context, request(mrTallow = FormAction.Rename("  ")))
		assertTrue(blank.hasBlankForm)
		assertFalse(blank.canProceed)

		val noop = useCase.check(
			context,
			request(name = "Robert Tallow", mrTallow = FormAction.Keep, tally = FormAction.Keep),
		)
		assertFalse(noop.hasChanges)

		val invalid = useCase.check(context, request(name = "Bad~Name"))
		assertEquals(EntryError.NAME_INVALID_CHARACTERS, invalid.entryError)
		assertNull(invalid.collisions.firstOrNull())
	}
}
