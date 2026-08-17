package repositories.references

import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaRepository
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContent
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.data.references.*
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

class BackfillEntryReferencesUseCaseTest : BaseTest() {

	@MockK
	private lateinit var sceneEditor: SceneEditorService

	@MockK
	private lateinit var referenceIndexService: ReferenceIndexService

	@MockK
	private lateinit var encyclopediaRepository: EncyclopediaRepository

	private lateinit var scrub: ScrubInvalidReferencesUseCase
	private lateinit var useCase: BackfillEntryReferencesUseCase

	private fun makeUseCase(
		config: ReferenceIndexConfig = ReferenceIndexConfig.default(),
	): BackfillEntryReferencesUseCase {
		return BackfillEntryReferencesUseCase(sceneEditor, referenceIndexService, scrub, config)
	}

	private fun person(id: Int, name: String, aliases: List<String> = emptyList()) =
		EntryContent(
			id = id,
			name = name,
			type = EntryType.PERSON,
			text = "",
			tags = emptySet(),
			aliases = aliases,
		)

	@BeforeEach
	override fun setup() {
		super.setup()
		MockKAnnotations.init(this, relaxUnitFun = true)
		every { encyclopediaRepository.findEntryDef(any()) } answers {
			val id = firstArg<Int>()
			EntryDef(getProject1Def(), id, EntryType.PERSON, "test entry $id")
		}
		scrub = ScrubInvalidReferencesUseCase(encyclopediaRepository)
		useCase = makeUseCase()
	}

	@Test
	fun `Adds entry to confirmedReferences of every matching scene`() = runTest(mainTestDispatcher) {
		val entry = person(42, "Bob")
		coEvery {
			referenceIndexService.findScenesMatchingEntry(42, listOf("Bob"))
		} returns listOf(10, 11)
		coEvery { sceneEditor.loadSceneMetadata(10) } returns SceneMetadata()
		coEvery { sceneEditor.loadSceneMetadata(11) } returns SceneMetadata(confirmedReferences = setOf(7))

		val storedById = mutableMapOf<Int, SceneMetadata>()
		val metaSlot = slot<SceneMetadata>()
		val idSlot = slot<Int>()
		coEvery { sceneEditor.storeMetadata(capture(metaSlot), capture(idSlot)) } answers {
			storedById[idSlot.captured] = metaSlot.captured
		}

		useCase(entry)

		// Scene 10 gains 42; scene 11 keeps its existing 7 and gains 42.
		assertEquals(setOf(42), storedById[10]?.confirmedReferences)
		assertEquals(setOf(7, 42), storedById[11]?.confirmedReferences)
	}

	@Test
	fun `Backfills entries of every type under the default config`() = runTest(mainTestDispatcher) {
		for ((index, type) in EntryType.entries.withIndex()) {
			val id = 100 + index
			val name = "Entry$id"
			val entry = EntryContent(
				id = id,
				name = name,
				type = type,
				text = "",
				tags = emptySet(),
				aliases = emptyList(),
			)
			coEvery { referenceIndexService.findScenesMatchingEntry(id, listOf(name)) } returns listOf(10)
			coEvery { sceneEditor.loadSceneMetadata(10) } returns SceneMetadata()
			val metaSlot = slot<SceneMetadata>()
			coEvery { sceneEditor.storeMetadata(capture(metaSlot), 10) } just Runs

			useCase(entry)

			assertEquals(setOf(id), metaSlot.captured.confirmedReferences, "type $type was not backfilled")
		}
	}

	@Test
	fun `Skips entry types that are not enabled in config`() = runTest(mainTestDispatcher) {
		// Config gates which entry types participate in matching. A type that is
		// not enabled never produces a scan or a write, regardless of name matches.
		val config = ReferenceIndexConfig(
			enabledEntryTypes = setOf(EntryType.PERSON), // PLACE not in this set
			enabledSourceTypes = setOf(ReferenceSourceType.Scene),
		)
		val placeEntry = EntryContent(
			id = 42,
			name = "Atlantis",
			type = EntryType.PLACE,
			text = "",
			tags = emptySet(),
			aliases = emptyList(),
		)

		makeUseCase(config).invoke(placeEntry)

		coVerify(exactly = 0) { referenceIndexService.findScenesMatchingEntry(any(), any()) }
		coVerify(exactly = 0) { sceneEditor.storeMetadata(any(), any()) }
	}

	@Test
	fun `No-op when no scenes match`() = runTest(mainTestDispatcher) {
		val entry = person(42, "Nonexistent")
		coEvery {
			referenceIndexService.findScenesMatchingEntry(42, listOf("Nonexistent"))
		} returns emptyList()

		useCase(entry)

		coVerify(exactly = 0) { sceneEditor.storeMetadata(any(), any()) }
	}

	@Test
	fun `Aliases are passed through to the matcher`() = runTest(mainTestDispatcher) {
		val entry = person(42, "Robert", aliases = listOf("Bob", "Bobby"))
		coEvery {
			referenceIndexService.findScenesMatchingEntry(
				42,
				listOf("Robert", "Bob", "Bobby"),
			)
		} returns emptyList()

		useCase(entry)

		coVerify {
			referenceIndexService.findScenesMatchingEntry(42, listOf("Robert", "Bob", "Bobby"))
		}
	}

	@Test
	fun `Defensively skips a scene that already confirms the entry`() = runTest(mainTestDispatcher) {
		// Belt-and-suspenders: even if the service incorrectly returns an
		// already-confirmed scene, the UseCase double-checks and does not double-add.
		val entry = person(42, "Bob")
		coEvery {
			referenceIndexService.findScenesMatchingEntry(42, listOf("Bob"))
		} returns listOf(10)
		coEvery { sceneEditor.loadSceneMetadata(10) } returns
			SceneMetadata(confirmedReferences = setOf(42))

		useCase(entry)

		coVerify(exactly = 0) { sceneEditor.storeMetadata(any(), any()) }
	}

	@Test
	fun `Defensively skips a scene that has dismissed the entry`() = runTest(mainTestDispatcher) {
		val entry = person(42, "Bob")
		coEvery {
			referenceIndexService.findScenesMatchingEntry(42, listOf("Bob"))
		} returns listOf(10)
		coEvery { sceneEditor.loadSceneMetadata(10) } returns
			SceneMetadata(dismissedReferences = setOf(42))

		useCase(entry)

		coVerify(exactly = 0) { sceneEditor.storeMetadata(any(), any()) }
	}
}
