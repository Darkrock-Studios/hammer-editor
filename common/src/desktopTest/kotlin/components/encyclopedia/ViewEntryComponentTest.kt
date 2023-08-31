package components.encyclopedia

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.backhandler.BackHandler
import com.arkivanov.essenty.lifecycle.Lifecycle
import com.arkivanov.essenty.statekeeper.StateKeeper
import com.darkrockstudios.apps.hammer.common.components.encyclopedia.ViewEntryComponent
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaRepository
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EntryError
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EntryResult
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContainer
import encyclopedia.fakeEntry
import getProject1Def
import io.mockk.*
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.koin.dsl.bind
import org.koin.dsl.module
import utils.BaseTest
import kotlin.test.Test
import kotlin.test.assertEquals

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
	private lateinit var encyclopediaRepository: EncyclopediaRepository

	@Before
	override fun setup() {
		super.setup()

		MockKAnnotations.init(this, relaxUnitFun = true)

		val testModule = module {
			single { encyclopediaRepository } bind EncyclopediaRepository::class
		}
		setupKoin(testModule)

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

		every { encyclopediaRepository.hasEntryImage(any(), any()) } returns false
		coEvery { encyclopediaRepository.loadEntry(entryDef = any()) } returns newContainer
		coEvery {
			encyclopediaRepository.updateEntry(
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
			closeEntry = {}
		)

		val result = comp.updateEntry(
			name = newName,
			text = oldEntry.text,
			tags = oldEntry.tags
		)
		assertEquals(EntryError.NONE, result.error)
	}

	@Test
	fun `Update Entry - Failure`() = runTest {
		val proj = getProject1Def()
		val oldEntry = fakeEntry()
		val origDef = oldEntry.toDef(proj)

		val newName = "A new - name"

		val newEntry = oldEntry.copy(name = newName)
		val newContainer = EntryContainer(newEntry)

		every { encyclopediaRepository.hasEntryImage(any(), any()) } returns false
		coEvery { encyclopediaRepository.loadEntry(entryDef = any()) } returns newContainer
		coEvery {
			encyclopediaRepository.updateEntry(
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
			closeEntry = {}
		)

		val result = comp.updateEntry(
			name = newName,
			text = oldEntry.text,
			tags = oldEntry.tags
		)
		assertEquals(EntryError.NAME_INVALID_CHARACTERS, result.error)
	}
}