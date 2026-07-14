package components.encyclopedia

import com.darkrockstudios.apps.hammer.common.components.encyclopedia.CreateEntryComponent
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaService
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EntryError
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EntryResult
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContainer
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContent
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.data.references.BackfillEntryReferencesUseCase
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import utils.ComponentTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CreateEntryComponentTest : ComponentTest() {

	private lateinit var encyclopediaService: EncyclopediaService
	private lateinit var backfillEntryReferences: BackfillEntryReferencesUseCase

	@BeforeEach
	override fun setup() {
		super.setup()

		encyclopediaService = mockk(relaxed = true)
		backfillEntryReferences = mockk(relaxed = true)

		setupComponentKoin(module {
			scope<ProjectDefScope> {
				scoped { encyclopediaService }
				scoped { backfillEntryReferences }
			}
		})
	}

	private fun newComponent() = CreateEntryComponent(
		componentContext = context,
		projectDef = projectDef,
	)

	@Test
	fun `a successful create reloads entries and backfills references`() = runTest(mainTestDispatcher) {
		val newEntry = EntryContent(
			id = 2,
			name = "Alice",
			type = EntryType.PERSON,
			text = "A person",
			tags = setOf("hero"),
		)
		val container = EntryContainer(newEntry)
		coEvery {
			encyclopediaService.createEntry(any(), any(), any(), any(), any(), any(), any())
		} returns EntryResult(container, EntryError.NONE)

		val comp = newComponent()
		context.resume()

		val result = comp.createEntry(
			name = "Alice",
			type = EntryType.PERSON,
			text = "A person",
			tags = setOf("hero"),
			imagePath = null,
		)
		advanceUntilIdle()

		assertEquals(EntryError.NONE, result.error)
		assertEquals(container, result.instance)
		coVerify {
			encyclopediaService.createEntry("Alice", EntryType.PERSON, "A person", setOf("hero"), null)
		}
		verify { encyclopediaService.loadEntries() }
		coVerify { backfillEntryReferences(newEntry) }
	}

	@Test
	fun `a failed create does not reload entries or backfill references`() = runTest(mainTestDispatcher) {
		coEvery {
			encyclopediaService.createEntry(any(), any(), any(), any(), any(), any(), any())
		} returns EntryResult(EntryError.NAME_TOO_LONG)

		val comp = newComponent()
		context.resume()

		val result = comp.createEntry(
			name = "A".repeat(1000),
			type = EntryType.PERSON,
			text = "text",
			tags = emptySet(),
			imagePath = null,
		)
		advanceUntilIdle()

		assertEquals(EntryError.NAME_TOO_LONG, result.error)
		verify(exactly = 0) { encyclopediaService.loadEntries() }
		coVerify(exactly = 0) { backfillEntryReferences(any()) }
	}

	@Test
	fun `confirmClose raises the confirmation dialog and dismissConfirmClose lowers it`() =
		runTest(mainTestDispatcher) {
			val comp = newComponent()
			context.resume()
			assertFalse(comp.state.value.showConfirmClose)

			comp.confirmClose()
			assertTrue(comp.state.value.showConfirmClose)

			comp.dismissConfirmClose()
			assertFalse(comp.state.value.showConfirmClose)
		}
}
