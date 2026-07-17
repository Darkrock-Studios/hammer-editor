package components.encyclopedia

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.backhandler.BackHandler
import com.arkivanov.essenty.lifecycle.Lifecycle
import com.arkivanov.essenty.statekeeper.StateKeeper
import com.darkrockstudios.apps.hammer.common.components.encyclopedia.ViewEntryComponent
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaService
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EntryError
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EntryResult
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContainer
import com.darkrockstudios.apps.hammer.common.data.references.BackfillEntryReferencesUseCase
import com.darkrockstudios.apps.hammer.common.data.references.ReferenceIndexService
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneRepository
import getProject1Def
import io.mockk.*
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.dsl.bind
import org.koin.dsl.module
import repositories.encyclopedia.fakeEntry
import utils.BaseTest
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class ViewEntryComponentTest : BaseTest() {

	@MockK
	lateinit var backHandler: BackHandler

	@MockK
	lateinit var stateKeeper: StateKeeper

	@MockK
	lateinit var lifecycle: Lifecycle

	@MockK
	private lateinit var context: ComponentContext

	@MockK
	private lateinit var encyclopediaService: EncyclopediaService

	private lateinit var backfillEntryReferences: BackfillEntryReferencesUseCase

	@BeforeEach
	override fun setup() {
		super.setup()

		MockKAnnotations.init(this, relaxUnitFun = true)

		backfillEntryReferences = mockk(relaxed = true)

		val testModule = module {
			single { encyclopediaService } bind EncyclopediaService::class
			single<ReferenceIndexService> { mockk(relaxed = true) }
			single<SceneRepository> { mockk(relaxed = true) }
			single { backfillEntryReferences } bind BackfillEntryReferencesUseCase::class
		}
		setupKoin(testModule)

		every { lifecycle.state } returns Lifecycle.State.STARTED
		every { context.lifecycle } returns lifecycle
		every { context.backHandler } returns backHandler
		every { context.stateKeeper } returns stateKeeper
		every { backHandler.register(any()) } just Runs
	}

	@Test
	fun `Update Entry - Success`() = runTest {
		val proj = getProject1Def()
		val oldEntry = fakeEntry()
		val origDef = oldEntry.toDef(proj)

		val newName = "A new name"

		val newEntry = oldEntry.copy(name = newName)
		val newContainer = EntryContainer(newEntry)

		every { encyclopediaService.findEntryImagePath(any()) } returns null
		every { encyclopediaService.findEntryImageExtension(any()) } returns null
		coEvery { encyclopediaService.calculateEntryImageHash(any(), any()) } returns null
		coEvery { encyclopediaService.loadEntry(entryDef = any()) } returns newContainer
		coEvery {
			encyclopediaService.updateEntry(
				oldEntryDef = origDef,
				name = newName,
				text = oldEntry.text,
				tags = oldEntry.tags
			)
		} returns EntryResult(newContainer, EntryError.NONE)

		val comp = ViewEntryComponent(
			componentContext = context,
			entryDef = origDef,
			addMenu = {},
			removeMenu = {},
			closeEntry = {},
			showScene = {},
			onShowGlobalSearchForTag = {},
		)

		val result = comp.updateEntry(
			name = newName,
			text = oldEntry.text,
			tags = oldEntry.tags
		)
		advanceUntilIdle()

		assertEquals(EntryError.NONE, result.error)
		assertEquals(newEntry.toDef(proj), comp.state.value.entryDef)
		assertEquals(newEntry, comp.state.value.content)
		coVerify { backfillEntryReferences(newEntry) }
	}

	@Test
	fun `Update Entry - Failure`() = runTest {
		val proj = getProject1Def()
		val oldEntry = fakeEntry()
		val origDef = oldEntry.toDef(proj)

		val newName = "A new - name"

		val newEntry = oldEntry.copy(name = newName)
		val newContainer = EntryContainer(newEntry)

		every { encyclopediaService.findEntryImagePath(any()) } returns null
		every { encyclopediaService.findEntryImageExtension(any()) } returns null
		coEvery { encyclopediaService.loadEntry(entryDef = any()) } returns newContainer
		coEvery {
			encyclopediaService.updateEntry(
				oldEntryDef = origDef,
				name = newName,
				text = oldEntry.text,
				tags = oldEntry.tags
			)
		} returns EntryResult(newContainer, EntryError.NAME_INVALID_CHARACTERS)

		val comp = ViewEntryComponent(
			componentContext = context,
			entryDef = origDef,
			addMenu = {},
			removeMenu = {},
			closeEntry = {},
			showScene = {},
			onShowGlobalSearchForTag = {},
		)

		val result = comp.updateEntry(
			name = newName,
			text = oldEntry.text,
			tags = oldEntry.tags
		)
		advanceUntilIdle()

		assertEquals(EntryError.NAME_INVALID_CHARACTERS, result.error)
		assertEquals(origDef, comp.state.value.entryDef)
		assertNull(comp.state.value.content)
		coVerify(exactly = 0) { backfillEntryReferences(any()) }
	}
}