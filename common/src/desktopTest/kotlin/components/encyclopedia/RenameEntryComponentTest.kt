package components.encyclopedia

import com.darkrockstudios.apps.hammer.common.components.encyclopedia.rename.RenameEntry.Step
import com.darkrockstudios.apps.hammer.common.components.encyclopedia.rename.RenameEntryComponent
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EntryError
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContent
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.rename.*
import com.darkrockstudios.apps.hammer.common.util.StrRes
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import utils.ComponentTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RenameEntryComponentTest : ComponentTest() {

	private lateinit var useCase: RenameEntryUseCase
	private lateinit var strRes: StrRes
	private val renamed = mutableListOf<EntryDef>()
	private var closeCount = 0
	private val requests = mutableListOf<RenameRequest>()

	private val entry = EntryContent(
		id = 7,
		name = "Robert Tallow",
		type = EntryType.PERSON,
		text = "",
		tags = emptySet(),
		aliases = listOf("Bob", "Tally"),
	)

	private val sceneKey = RenameItemKey(RenamePlace.SCENE_TEXT, 1)
	private val noteKey = RenameItemKey(RenamePlace.NOTE, 2)
	private val sure1 = match(sceneKey, 0)
	private val sure2 = match(sceneKey, 30)
	private val unsure = match(sceneKey, 60, UnsureReason.Plural)
	private val noteMatch = match(noteKey, 0)

	private fun match(item: RenameItemKey, offset: Int, reason: UnsureReason? = null) =
		RenameMatch(RenameMatchKey(item, offset, "Robert Tallow"), "Robert Tolliver", reason, null)

	private fun preview(request: RenameRequest) = RenamePreview(
		request = request,
		oldName = "Robert Tallow",
		newName = "Robert Tolliver",
		newAliases = listOf("Bob"),
		items = listOf(
			RenameItem(sceneKey, "Scene 1", listOf(sure1, sure2, unsure)),
			RenameItem(noteKey, "A note", listOf(noteMatch)),
		),
		backupSupported = true,
	)

	private val result = RenameResult(
		replaced = 3,
		itemsChanged = 2,
		skippedStale = 0,
		skippedInvalid = 0,
		backupMade = true,
		backupFailed = false,
		entryError = EntryError.NONE,
		newEntryDef = null,
	)

	@BeforeEach
	override fun setup() {
		super.setup()
		useCase = mockk()
		strRes = mockk()
		coEvery { strRes.get(any(), *anyVararg()) } returns "Before renaming Robert Tallow"
		coEvery { useCase.loadContext(entry.id) } returns RenameContext(entry, emptyList())
		every { useCase.check(any(), capture(requests)) } answers {
			val request = secondArg<RenameRequest>()
			val newName = RenameEntryUseCase.newName(request)
			RenameCheck(
				newName = newName,
				newAliases = RenameEntryUseCase.newAliases(request, newName),
				entryError = if (newName.isEmpty()) EntryError.NAME_TOO_SHORT else EntryError.NONE,
				hasBlankForm = false,
				collisions = emptyList(),
				hasChanges = newName != entry.name,
			)
		}
		coEvery { useCase.preview(any(), any()) } answers { preview(firstArg()) }
		setupComponentKoin(module {
			single { useCase }
			single { strRes }
		})
		renamed.clear()
		requests.clear()
		closeCount = 0
	}

	private fun newComponent() = RenameEntryComponent(
		componentContext = context,
		projectDef = projectDef,
		entryId = entry.id,
		onRenamed = { renamed.add(it) },
		onClose = { closeCount++ },
	)

	@Test
	fun `Opens on the entry's name and aliases`() = runTest(mainTestDispatcher) {
		val component = newComponent()
		context.resume()
		advanceUntilIdle()

		val state = component.state.value
		assertEquals(Step.Names, state.step)
		assertEquals("Robert Tallow", state.name.text)
		assertEquals(listOf("Bob", "Tally"), state.aliases.map { it.from })
		assertFalse(state.check!!.canProceed)
	}

	@Test
	fun `Edits become the request's mappings`() = runTest(mainTestDispatcher) {
		val component = newComponent()
		context.resume()
		advanceUntilIdle()

		component.updateName("Robert Tolliver ")
		component.setAliasRemoved(1, true)
		component.addAlias()
		component.updateAddedAlias(0, "Rob")

		val request = requests.last()
		assertEquals(FormMapping("Robert Tallow", FormAction.Rename("Robert Tolliver ")), request.name)
		assertEquals(
			listOf(FormMapping("Bob", FormAction.Keep), FormMapping("Tally", FormAction.Remove)),
			request.aliases,
		)
		assertEquals(listOf("Rob"), request.addedAliases)
		assertTrue(component.state.value.check!!.canProceed)
	}

	@Test
	fun `Finding references is refused until the request can proceed`() = runTest(mainTestDispatcher) {
		val component = newComponent()
		context.resume()
		advanceUntilIdle()

		component.findReferences()
		advanceUntilIdle()

		assertEquals(Step.Names, component.state.value.step)
		coVerify(exactly = 0) { useCase.preview(any(), any()) }
	}

	@Test
	fun `Summary counts sure matches and follows exclusions and inclusions`() = runTest(mainTestDispatcher) {
		val component = newComponent()
		context.resume()
		advanceUntilIdle()
		component.updateName("Robert Tolliver")

		component.findReferences()
		advanceUntilIdle()

		val summary = assertIs<Step.Summary>(component.state.value.step)
		assertEquals(3, summary.pendingCount)
		assertEquals(1, summary.unsureCount)

		component.setItemExcluded(noteKey, true)
		component.setUnsureIncluded(unsure.key, true)
		assertEquals(3, (component.state.value.step as Step.Summary).pendingCount)

		component.setItemExcluded(noteKey, false)
		assertEquals(4, (component.state.value.step as Step.Summary).pendingCount)

		component.backToNames()
		assertEquals(Step.Names, component.state.value.step)
	}

	@Test
	fun `Apply passes the choices, reports the rename and cannot be closed while writing`() =
		runTest(mainTestDispatcher) {
			val gate = CompletableDeferred<Unit>()
			val renamedDef = EntryDef(projectDef, entry.id, EntryType.PERSON, "Robert Tolliver")
			val excluded = slot<Set<RenameItemKey>>()
			val included = slot<Set<RenameMatchKey>>()
			coEvery {
				useCase.apply(any(), capture(excluded), capture(included), "Before renaming Robert Tallow", any())
			} coAnswers {
				gate.await()
				result.copy(newEntryDef = renamedDef)
			}

			val component = newComponent()
			context.resume()
			advanceUntilIdle()
			component.updateName("Robert Tolliver")
			component.findReferences()
			advanceUntilIdle()
			component.setItemExcluded(noteKey, true)
			component.setUnsureIncluded(unsure.key, true)

			component.apply()
			advanceUntilIdle()
			assertIs<Step.Applying>(component.state.value.step)
			component.close()
			assertTrue(context.back())
			assertEquals(0, closeCount)

			gate.complete(Unit)
			advanceUntilIdle()

			assertIs<Step.Done>(component.state.value.step)
			assertEquals(setOf(noteKey), excluded.captured)
			assertEquals(setOf(unsure.key), included.captured)
			assertEquals(listOf(renamedDef), renamed)

			assertTrue(context.back())
			assertEquals(1, closeCount)
		}
}
