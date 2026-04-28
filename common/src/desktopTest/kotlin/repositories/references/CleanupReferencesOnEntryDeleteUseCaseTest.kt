package repositories.references

import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaRepository
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.data.references.CleanupReferencesOnEntryDeleteUseCase
import com.darkrockstudios.apps.hammer.common.data.references.ReferenceIndexService
import com.darkrockstudios.apps.hammer.common.data.references.ScrubInvalidReferencesUseCase
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.scenemetadata.SceneMetadata
import getProject1Def
import io.mockk.*
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import utils.BaseTest
import kotlin.test.assertEquals

class CleanupReferencesOnEntryDeleteUseCaseTest : BaseTest() {

	@MockK
	private lateinit var sceneEditor: SceneEditorRepository

	@MockK
	private lateinit var referenceIndexService: ReferenceIndexService

	@MockK
	private lateinit var encyclopediaRepository: EncyclopediaRepository

	private lateinit var scrub: ScrubInvalidReferencesUseCase
	private lateinit var useCase: CleanupReferencesOnEntryDeleteUseCase

	@BeforeEach
	override fun setup() {
		super.setup()
		MockKAnnotations.init(this, relaxUnitFun = true)
		every { encyclopediaRepository.findEntryDef(any()) } answers {
			val id = firstArg<Int>()
			EntryDef(getProject1Def(), id, EntryType.PERSON, "test entry $id")
		}
		scrub = ScrubInvalidReferencesUseCase(encyclopediaRepository)
		useCase = CleanupReferencesOnEntryDeleteUseCase(sceneEditor, referenceIndexService, scrub)
	}

	@Test
	fun `Removes deleted id from confirmedReferences of every scene the cache knows about`() =
		runTest(mainTestDispatcher) {
			coEvery { referenceIndexService.getScenesReferencing(42) } returns setOf(10, 11)
			coEvery { sceneEditor.loadSceneMetadata(10) } returns
				SceneMetadata(confirmedReferences = setOf(7, 42))
			coEvery { sceneEditor.loadSceneMetadata(11) } returns
				SceneMetadata(confirmedReferences = setOf(42))

			val written = mutableMapOf<Int, SceneMetadata>()
			val metaSlot = slot<SceneMetadata>()
			val idSlot = slot<Int>()
			coEvery { sceneEditor.storeMetadata(capture(metaSlot), capture(idSlot)) } answers {
				written[idSlot.captured] = metaSlot.captured
			}

			useCase(42)

			assertEquals(setOf(7), written[10]?.confirmedReferences)
			assertEquals(emptySet(), written[11]?.confirmedReferences)
		}

	@Test
	fun `Also drops the deleted id from dismissedReferences in the same write`() =
		runTest(mainTestDispatcher) {
			// Free hygiene: while we're already writing the scene, drop any leftover
			// dismissed entry of the same id so the file is fully clean.
			coEvery { referenceIndexService.getScenesReferencing(42) } returns setOf(10)
			coEvery { sceneEditor.loadSceneMetadata(10) } returns SceneMetadata(
				confirmedReferences = setOf(42),
				dismissedReferences = setOf(42, 99),
			)

			val metaSlot = slot<SceneMetadata>()
			coEvery { sceneEditor.storeMetadata(capture(metaSlot), 10) } returns Unit

			useCase(42)

			assertEquals(emptySet(), metaSlot.captured.confirmedReferences)
			assertEquals(setOf(99), metaSlot.captured.dismissedReferences)
		}

	@Test
	fun `Does nothing when the cache reports no scenes reference the entry`() =
		runTest(mainTestDispatcher) {
			// Lazy fallback: scenes that have the entry only in dismissedReferences
			// are not in the cache and will heal at next save via scrub.
			coEvery { referenceIndexService.getScenesReferencing(42) } returns emptySet()

			useCase(42)

			coVerify(exactly = 0) { sceneEditor.loadSceneMetadata(any()) }
			coVerify(exactly = 0) { sceneEditor.storeMetadata(any(), any()) }
		}

	@Test
	fun `Skips a scene whose metadata does not actually contain the deleted id`() =
		runTest(mainTestDispatcher) {
			// Defensive: if the cache claims a scene confirms the entry but the on-disk
			// metadata has already been edited away, don't write a no-op file just to
			// produce a no-op delta. Keeps the system resilient to stale-cache races.
			coEvery { referenceIndexService.getScenesReferencing(42) } returns setOf(10)
			coEvery { sceneEditor.loadSceneMetadata(10) } returns
				SceneMetadata(confirmedReferences = setOf(7))

			useCase(42)

			coVerify(exactly = 0) { sceneEditor.storeMetadata(any(), any()) }
		}

	@Test
	fun `Writes each affected scene exactly once`() = runTest(mainTestDispatcher) {
		// Per-scene single rewrite is the whole point of cache-driven cleanup -
		// without it we'd be doing one storeMetadata per (scene x entry) which
		// would be quadratic in the worst case.
		coEvery { referenceIndexService.getScenesReferencing(42) } returns setOf(10, 11, 12)
		coEvery { sceneEditor.loadSceneMetadata(10) } returns SceneMetadata(confirmedReferences = setOf(42))
		coEvery { sceneEditor.loadSceneMetadata(11) } returns SceneMetadata(confirmedReferences = setOf(42))
		coEvery { sceneEditor.loadSceneMetadata(12) } returns SceneMetadata(confirmedReferences = setOf(42))

		useCase(42)

		coVerify(exactly = 1) { sceneEditor.storeMetadata(any(), 10) }
		coVerify(exactly = 1) { sceneEditor.storeMetadata(any(), 11) }
		coVerify(exactly = 1) { sceneEditor.storeMetadata(any(), 12) }
	}
}
