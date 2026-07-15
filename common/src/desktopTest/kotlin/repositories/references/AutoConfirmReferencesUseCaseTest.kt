package repositories.references

import com.darkrockstudios.apps.hammer.common.data.SceneBuffer
import com.darkrockstudios.apps.hammer.common.data.SceneContent
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.UpdateSource
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaRepository
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.data.references.AutoConfirmReferencesUseCase
import com.darkrockstudios.apps.hammer.common.data.references.EntrySuggestion
import com.darkrockstudios.apps.hammer.common.data.references.ReferenceIndexService
import com.darkrockstudios.apps.hammer.common.data.references.ScrubInvalidReferencesUseCase
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorService
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.scenemetadata.SceneMetadata
import getProject1Def
import io.mockk.*
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import utils.BaseTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AutoConfirmReferencesUseCaseTest : BaseTest() {

	@MockK
	private lateinit var sceneEditor: SceneEditorService

	@MockK
	private lateinit var referenceIndexService: ReferenceIndexService

	@MockK
	private lateinit var encyclopediaRepository: EncyclopediaRepository

	private lateinit var scrub: ScrubInvalidReferencesUseCase
	private lateinit var useCase: AutoConfirmReferencesUseCase

	private val sceneItem = SceneItem(
		projectDef = getProject1Def(),
		type = SceneItem.Type.Scene,
		id = 7,
		name = "Test Scene",
		order = 0,
	)

	@BeforeEach
	override fun setup() {
		super.setup()
		MockKAnnotations.init(this, relaxUnitFun = true)
		// Default: every id resolves so the scrub step doesn't strip valid data.
		every { encyclopediaRepository.findEntryDef(any()) } answers {
			val id = firstArg<Int>()
			EntryDef(getProject1Def(), id, EntryType.PERSON, "test entry $id")
		}
		scrub = ScrubInvalidReferencesUseCase(encyclopediaRepository)
		useCase = AutoConfirmReferencesUseCase(sceneEditor, referenceIndexService, scrub)
	}

	private fun bufferWith(text: String) = SceneBuffer(
		content = SceneContent(scene = sceneItem, markdown = text),
		dirty = true,
		source = UpdateSource.Editor,
	)

	@Test
	fun `Adds matched entry IDs to confirmedReferences and stores metadata`() = runTest(mainTestDispatcher) {
		every { sceneEditor.getSceneBuffer(sceneItem) } returns bufferWith("Bob walked away.")
		coEvery { sceneEditor.loadSceneMetadata(sceneItem.id) } returns SceneMetadata()
		coEvery {
			referenceIndexService.computeAutoReferencesForScene(
				sceneId = sceneItem.id,
				sceneText = "Bob walked away.",
				metadata = SceneMetadata(),
			)
		} returns listOf(EntrySuggestion(entryId = 42, matchedAlias = "Bob"))

		val storedSlot = slot<SceneMetadata>()
		coEvery { sceneEditor.storeMetadata(capture(storedSlot), sceneItem.id) } returns Unit

		useCase(sceneItem)

		coVerify(exactly = 1) { sceneEditor.storeMetadata(any(), sceneItem.id) }
		assertEquals(setOf(42), storedSlot.captured.confirmedReferences)
	}

	@Test
	fun `Preserves existing confirmed references when adding new ones`() = runTest(mainTestDispatcher) {
		// Sticky-confirmed: already-confirmed entries stay; new matches are merged in.
		every { sceneEditor.getSceneBuffer(sceneItem) } returns bufferWith("Bob and Alice met.")
		coEvery { sceneEditor.loadSceneMetadata(sceneItem.id) } returns
			SceneMetadata(confirmedReferences = setOf(7))
		coEvery {
			referenceIndexService.computeAutoReferencesForScene(any(), any(), any())
		} returns listOf(EntrySuggestion(entryId = 42, matchedAlias = "Bob"))

		val storedSlot = slot<SceneMetadata>()
		coEvery { sceneEditor.storeMetadata(capture(storedSlot), sceneItem.id) } returns Unit

		useCase(sceneItem)

		assertEquals(setOf(7, 42), storedSlot.captured.confirmedReferences)
	}

	@Test
	fun `Does not write when matcher returns no new entries`() = runTest(mainTestDispatcher) {
		// No-op fast path: no matches means no metadata write means no spurious sync churn.
		every { sceneEditor.getSceneBuffer(sceneItem) } returns bufferWith("Quiet text.")
		coEvery { sceneEditor.loadSceneMetadata(sceneItem.id) } returns SceneMetadata()
		coEvery {
			referenceIndexService.computeAutoReferencesForScene(any(), any(), any())
		} returns emptyList()

		useCase(sceneItem)

		coVerify(exactly = 0) { sceneEditor.storeMetadata(any(), any()) }
	}

	@Test
	fun `Does nothing when buffer is not loaded`() = runTest(mainTestDispatcher) {
		// Defensive: a UseCase invoked on a scene without an in-memory buffer returns
		// without touching the matcher or storage. No NPE, no spurious calls.
		every { sceneEditor.getSceneBuffer(sceneItem) } returns null

		useCase(sceneItem)

		coVerify(exactly = 0) {
			referenceIndexService.computeAutoReferencesForScene(any(), any(), any())
		}
		coVerify(exactly = 0) { sceneEditor.storeMetadata(any(), any()) }
	}

	@Test
	fun `Scrubs orphan IDs from existing metadata in the same write`() = runTest(mainTestDispatcher) {
		// If the metadata already carries an orphan reference (e.g. an entry was
		// deleted) and a new auto-match comes in, both happen in one storeMetadata
		// call: orphan dropped, new ref added.
		every { encyclopediaRepository.findEntryDef(99) } returns null
		every { sceneEditor.getSceneBuffer(sceneItem) } returns bufferWith("Bob walked away.")
		coEvery { sceneEditor.loadSceneMetadata(sceneItem.id) } returns
			SceneMetadata(confirmedReferences = setOf(7, 99))
		coEvery {
			referenceIndexService.computeAutoReferencesForScene(any(), any(), any())
		} returns listOf(EntrySuggestion(entryId = 42, matchedAlias = "Bob"))

		val storedSlot = slot<SceneMetadata>()
		coEvery { sceneEditor.storeMetadata(capture(storedSlot), sceneItem.id) } returns Unit

		useCase(sceneItem)

		// 99 dropped (orphan), 7 kept (still resolves), 42 added (new auto-match).
		assertEquals(setOf(7, 42), storedSlot.captured.confirmedReferences)
		assertTrue(99 !in storedSlot.captured.confirmedReferences)
	}
}
