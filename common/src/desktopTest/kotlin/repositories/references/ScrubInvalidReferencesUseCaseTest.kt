package repositories.references

import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaRepository
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.data.references.ScrubInvalidReferencesUseCase
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.scenemetadata.SceneMetadata
import getProject1Def
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class ScrubInvalidReferencesUseCaseTest {

	@MockK
	private lateinit var encyclopediaRepository: EncyclopediaRepository

	private lateinit var scrub: ScrubInvalidReferencesUseCase

	@BeforeEach
	fun setup() {
		MockKAnnotations.init(this, relaxUnitFun = true)
		// Default: every id resolves. Per-test overrides null out specific orphan ids.
		every { encyclopediaRepository.findEntryDef(any()) } answers {
			val id = firstArg<Int>()
			EntryDef(getProject1Def(), id, EntryType.PERSON, "test entry $id")
		}
		scrub = ScrubInvalidReferencesUseCase(encyclopediaRepository)
	}

	@Test
	fun `Drops confirmed references whose entry no longer resolves`() {
		every { encyclopediaRepository.findEntryDef(99) } returns null

		val metadata = SceneMetadata(confirmedReferences = setOf(7, 99, 11))

		val result = scrub(metadata)

		assertEquals(setOf(7, 11), result.confirmedReferences)
	}

	@Test
	fun `Drops dismissed references whose entry no longer resolves`() {
		every { encyclopediaRepository.findEntryDef(88) } returns null

		val metadata = SceneMetadata(dismissedReferences = setOf(11, 88))

		val result = scrub(metadata)

		assertEquals(setOf(11), result.dismissedReferences)
	}

	@Test
	fun `Cleans confirmed and dismissed in a single pass`() {
		every { encyclopediaRepository.findEntryDef(99) } returns null
		every { encyclopediaRepository.findEntryDef(88) } returns null

		val metadata = SceneMetadata(
			confirmedReferences = setOf(7, 99),
			dismissedReferences = setOf(11, 88),
		)

		val result = scrub(metadata)

		assertEquals(setOf(7), result.confirmedReferences)
		assertEquals(setOf(11), result.dismissedReferences)
	}

	@Test
	fun `Returns the same instance when no orphans are present`() {
		// Identity preservation matters because callers may rely on referential
		// equality to skip no-op writes (and to avoid spurious cache deltas).
		val metadata = SceneMetadata(
			confirmedReferences = setOf(7, 11),
			dismissedReferences = setOf(42),
		)

		val result = scrub(metadata)

		assertSame(metadata, result)
	}

	@Test
	fun `Returns the same instance when both reference sets are empty`() {
		// Fast path: don't even consult the encyclopedia when there's nothing to check.
		val metadata = SceneMetadata(
			outline = "outline",
			notes = "notes",
			currentDraftName = "draft",
		)

		val result = scrub(metadata)

		assertSame(metadata, result)
	}

	@Test
	fun `Preserves non-reference fields when scrubbing`() {
		every { encyclopediaRepository.findEntryDef(99) } returns null

		val metadata = SceneMetadata(
			outline = "outline text",
			notes = "notes text",
			currentDraftName = "draft 1",
			confirmedReferences = setOf(99),
		)

		val result = scrub(metadata)

		assertEquals("outline text", result.outline)
		assertEquals("notes text", result.notes)
		assertEquals("draft 1", result.currentDraftName)
		assertEquals(emptySet(), result.confirmedReferences)
	}
}
