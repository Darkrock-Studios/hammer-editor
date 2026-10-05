package components.encyclopedia

import com.darkrockstudios.apps.hammer.common.components.encyclopedia.ViewEntryComponent
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaService
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContainer
import com.darkrockstudios.apps.hammer.common.data.references.BackfillEntryReferencesUseCase
import com.darkrockstudios.apps.hammer.common.data.references.CleanupReferencesOnEntryDeleteUseCase
import com.darkrockstudios.apps.hammer.common.data.references.ReferenceIndexService
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import okio.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import repositories.encyclopedia.fakeEntry
import utils.ComponentTest
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class ViewEntryComponentExternalChangeTest : ComponentTest() {

	private lateinit var encyclopediaService: EncyclopediaService
	private lateinit var entryChangedFlow: MutableSharedFlow<Int>

	@BeforeEach
	override fun setup() {
		super.setup()

		entryChangedFlow = MutableSharedFlow(extraBufferCapacity = 8)
		encyclopediaService = mockk(relaxed = true)
		every { encyclopediaService.entryChangedFlow } returns entryChangedFlow
		every { encyclopediaService.findEntryImagePath(any()) } returns null
		every { encyclopediaService.findEntryImageExtension(any()) } returns null

		val referenceIndexService = mockk<ReferenceIndexService>(relaxed = true)
		every { referenceIndexService.flowForEntry(any()) } returns emptyFlow()

		setupComponentKoin(module {
			single { encyclopediaService }
			single { referenceIndexService }
			single<SceneEditorService> { mockk(relaxed = true) }
			single<BackfillEntryReferencesUseCase> { mockk(relaxed = true) }
			single<CleanupReferencesOnEntryDeleteUseCase> { mockk(relaxed = true) }
		})
	}

	@Test
	fun `An entry renamed and edited elsewhere refreshes the open view`() = runTest(mainTestDispatcher) {
		val original = fakeEntry()
		val originalDef = original.toDef(projectDef)
		every { encyclopediaService.loadEntry(originalDef) } returns EntryContainer(original)

		val comp = ViewEntryComponent(
			componentContext = context,
			entryDef = originalDef,
			addMenu = {},
			removeMenu = {},
			closeEntry = {},
			showScene = {},
			onShowGlobalSearchForTag = {},
		)
		context.resume()
		advanceUntilIdle()
		assertEquals(original, comp.state.value.content)

		val synced = original.copy(name = "Server Name", text = "Server text")
		val syncedDef = synced.toDef(projectDef)
		every { encyclopediaService.findEntryDef(original.id) } returns syncedDef
		every { encyclopediaService.loadEntry(syncedDef) } returns EntryContainer(synced)

		entryChangedFlow.emit(original.id)
		advanceUntilIdle()

		assertEquals(syncedDef, comp.state.value.entryDef)
		assertEquals(synced, comp.state.value.content)
	}

	@Test
	fun `A failed refresh does not stop later refreshes`() = runTest(mainTestDispatcher) {
		val original = fakeEntry()
		val originalDef = original.toDef(projectDef)
		every { encyclopediaService.loadEntry(originalDef) } returns EntryContainer(original)
		every { encyclopediaService.findEntryDef(original.id) } returns originalDef

		val comp = ViewEntryComponent(
			componentContext = context,
			entryDef = originalDef,
			addMenu = {},
			removeMenu = {},
			closeEntry = {},
			showScene = {},
			onShowGlobalSearchForTag = {},
		)
		context.resume()
		advanceUntilIdle()

		every { encyclopediaService.loadEntry(originalDef) } throws IOException("mid-move")
		entryChangedFlow.emit(original.id)
		advanceUntilIdle()
		assertEquals(original, comp.state.value.content)

		val synced = original.copy(text = "Server text")
		every { encyclopediaService.loadEntry(originalDef) } returns EntryContainer(synced)
		entryChangedFlow.emit(original.id)
		advanceUntilIdle()

		assertEquals(synced, comp.state.value.content)
	}

	@Test
	fun `A change to another entry does not reload this one`() = runTest(mainTestDispatcher) {
		val original = fakeEntry()
		val originalDef = original.toDef(projectDef)
		every { encyclopediaService.loadEntry(originalDef) } returns EntryContainer(original)

		ViewEntryComponent(
			componentContext = context,
			entryDef = originalDef,
			addMenu = {},
			removeMenu = {},
			closeEntry = {},
			showScene = {},
			onShowGlobalSearchForTag = {},
		)
		context.resume()
		advanceUntilIdle()

		entryChangedFlow.emit(original.id + 1)
		advanceUntilIdle()

		verify(exactly = 0) { encyclopediaService.findEntryDef(any()) }
		verify(exactly = 1) { encyclopediaService.loadEntry(originalDef) }
	}
}
