package components.encyclopedia

import com.darkrockstudios.apps.hammer.common.components.encyclopedia.Encyclopedia
import com.darkrockstudios.apps.hammer.common.components.encyclopedia.EncyclopediaComponent
import com.darkrockstudios.apps.hammer.common.components.projectroot.CloseConfirm
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaService
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContainer
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContent
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.data.references.BackfillEntryReferencesUseCase
import com.darkrockstudios.apps.hammer.common.data.references.CleanupReferencesOnEntryDeleteUseCase
import com.darkrockstudios.apps.hammer.common.data.references.ReferenceIndexService
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorService
import com.darkrockstudios.apps.hammer.common.data.tagindex.TagIndex
import com.darkrockstudios.apps.hammer.common.data.tagindex.TagIndexService
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
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

@OptIn(ExperimentalCoroutinesApi::class)
class EncyclopediaComponentTest : ComponentTest() {

	private lateinit var encyclopediaService: EncyclopediaService

	private val entryContent = EntryContent(
		id = 1,
		name = "Alice",
		type = EntryType.PERSON,
		text = "A person of interest",
		tags = setOf("hero"),
	)

	private val entryDef = EntryDef(
		projectDef = projectDef,
		id = entryContent.id,
		type = entryContent.type,
		name = entryContent.name,
	)

	@BeforeEach
	override fun setup() {
		super.setup()

		encyclopediaService = mockk(relaxed = true)
		every { encyclopediaService.entryListFlow } returns MutableStateFlow(emptyList<EntryDef>())
		every { encyclopediaService.loadEntry(any<EntryDef>()) } returns EntryContainer(entryContent)
		every { encyclopediaService.findEntryImagePath(any()) } returns null
		every { encyclopediaService.findEntryImageExtension(any()) } returns null

		val referenceIndexService = mockk<ReferenceIndexService>(relaxed = true)
		every { referenceIndexService.flowForEntry(any()) } returns emptyFlow()

		val tagIndexService = mockk<TagIndexService>(relaxed = true)
		every { tagIndexService.tagIndex } returns MutableStateFlow(TagIndex.EMPTY)

		setupComponentKoin(module {
			scope<ProjectDefScope> {
				scoped { encyclopediaService }
				scoped { referenceIndexService }
				scoped { tagIndexService }
				scoped { mockk<SceneEditorService>(relaxed = true) }
				scoped { mockk<BackfillEntryReferencesUseCase>(relaxed = true) }
				scoped { mockk<CleanupReferencesOnEntryDeleteUseCase>(relaxed = true) }
			}
		})
	}

	private fun newComponent() = EncyclopediaComponent(
		componentContext = context,
		projectDef = projectDef,
		updateShouldClose = {},
		addMenu = {},
		removeMenu = {},
		showScene = {},
		onShowGlobalSearchForTag = {},
	)

	@Test
	fun `the browse screen is the initial destination`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		assertIs<Encyclopedia.Destination.BrowseEntriesDestination>(comp.stack.value.active.instance)
		assertTrue(comp.isAtRoot())
		assertEquals(emptySet(), comp.shouldConfirmClose())
	}

	@Test
	fun `showViewEntry pushes the view destination for that entry`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		comp.showViewEntry(entryDef)
		advanceUntilIdle()

		val destination = assertIs<Encyclopedia.Destination.ViewEntryDestination>(comp.stack.value.active.instance)
		assertEquals(entryDef, destination.component.state.value.entryDef)
		assertFalse(comp.isAtRoot())
	}

	@Test
	fun `showCreateEntry pushes the create destination which always requires close confirmation`() =
		runTest(mainTestDispatcher) {
			val comp = newComponent()
			context.resume()
			advanceUntilIdle()

			comp.showCreateEntry()
			advanceUntilIdle()

			assertIs<Encyclopedia.Destination.CreateEntryDestination>(comp.stack.value.active.instance)
			assertEquals(setOf(CloseConfirm.Encyclopedia), comp.shouldConfirmClose())
		}

	@Test
	fun `viewing an entry without editing does not require close confirmation`() =
		runTest(mainTestDispatcher) {
			val comp = newComponent()
			context.resume()
			advanceUntilIdle()

			comp.showViewEntry(entryDef)
			advanceUntilIdle()

			assertEquals(emptySet(), comp.shouldConfirmClose())
		}

	@Test
	fun `an in-progress entry edit requires close confirmation`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()
		comp.showViewEntry(entryDef)
		advanceUntilIdle()

		val destination = assertIs<Encyclopedia.Destination.ViewEntryDestination>(comp.stack.value.active.instance)
		destination.component.startNameEdit()

		assertEquals(setOf(CloseConfirm.Encyclopedia), comp.shouldConfirmClose())

		destination.component.finishNameEdit()

		assertEquals(emptySet(), comp.shouldConfirmClose())
	}

	@Test
	fun `showBrowse pops back to the browse root`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()
		comp.showViewEntry(entryDef)
		comp.showCreateEntry()
		advanceUntilIdle()

		comp.showBrowse()
		advanceUntilIdle()

		assertIs<Encyclopedia.Destination.BrowseEntriesDestination>(comp.stack.value.active.instance)
		assertEquals(1, comp.stack.value.items.size)
		assertTrue(comp.isAtRoot())
	}

	@Test
	fun `onBack pops the top destination`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()
		comp.showViewEntry(entryDef)
		advanceUntilIdle()

		comp.onBack()
		advanceUntilIdle()

		assertIs<Encyclopedia.Destination.BrowseEntriesDestination>(comp.stack.value.active.instance)
	}

	@Test
	fun `onBack at the browse root does nothing`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		comp.onBack()
		advanceUntilIdle()

		assertIs<Encyclopedia.Destination.BrowseEntriesDestination>(comp.stack.value.active.instance)
		assertEquals(1, comp.stack.value.items.size)
	}

	@Test
	fun `showViewEntry brings an already-open entry back to the front`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()
		comp.showViewEntry(entryDef)
		comp.showCreateEntry()
		advanceUntilIdle()

		comp.showViewEntry(entryDef)
		advanceUntilIdle()

		val destination = assertIs<Encyclopedia.Destination.ViewEntryDestination>(comp.stack.value.active.instance)
		assertEquals(entryDef, destination.component.state.value.entryDef)
		assertEquals(3, comp.stack.value.items.size)
	}
}
