package repositories.spellcheck

import PROJECT_EMPTY_NAME
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaDatasource
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaRepository
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettings
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.globalsettings.SpellCheckerSettings
import com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource.GlobalSettingsDatasource
import com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource.ServerSettingsDatasource
import com.darkrockstudios.apps.hammer.common.data.id.IdAllocator
import com.darkrockstudios.apps.hammer.common.data.projectdata.ProjectDataDatasource
import com.darkrockstudios.apps.hammer.common.data.projectdata.ProjectDataRepository
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncDataDatasource
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncJournal
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.common.fileio.ExternalFileIo
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import com.darkrockstudios.apps.hammer.common.spellcheck.ProjectDictionaryService
import com.darkrockstudios.apps.hammer.common.spellcheck.ProjectSpellCheckRepository
import com.darkrockstudios.apps.hammer.common.spellcheck.SpellCheckRepository
import com.darkrockstudios.apps.hammer.common.util.Locale
import com.darkrockstudios.libs.platformspellchecker.PlatformSpellChecker
import com.darkrockstudios.libs.platformspellchecker.PlatformSpellCheckerFactory
import createProject
import getProjectDef
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import utils.BaseTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Classical-style: a real [ProjectDictionaryService] over a real [EncyclopediaRepository],
 * [ProjectDataRepository], [GlobalSettingsStore] and [SpellCheckRepository] (FakeFileSystem-backed).
 * Only the platform spell checker factory and settings/sync persistence are mocked.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProjectDictionaryServiceTest : BaseTest() {

	private val projectDef = getProjectDef(PROJECT_EMPTY_NAME)

	private lateinit var globalSettingsDatasource: GlobalSettingsDatasource
	private lateinit var serverSettingsDatasource: ServerSettingsDatasource
	private lateinit var factory: PlatformSpellCheckerFactory
	private lateinit var fileSystem: FakeFileSystem
	private lateinit var idAllocator: IdAllocator
	private lateinit var syncJournal: SyncJournal
	private lateinit var datasource: EncyclopediaDatasource
	private lateinit var encyclopediaRepository: EncyclopediaRepository
	private lateinit var projectDataRepository: ProjectDataRepository
	private lateinit var globalSettingsStore: GlobalSettingsStore
	private lateinit var spellCheckRepository: SpellCheckRepository

	/** Words applied per created checker, in creation order. Empty set = checker created with no session words. */
	private val appliedPerChecker = mutableListOf<Set<String>>()

	private var nextId = 1

	@BeforeEach
	override fun setup() {
		super.setup()
		globalSettingsDatasource = mockk()
		serverSettingsDatasource = mockk()
		factory = mockk()
		idAllocator = mockk()
		syncJournal = mockk()
		appliedPerChecker.clear()
		nextId = 1

		every { factory.hasLanguage(any()) } returns true
		coEvery { factory.createSpellChecker(any()) } answers {
			val index = appliedPerChecker.size
			appliedPerChecker.add(emptySet())
			val checker = mockk<PlatformSpellChecker>()
			coEvery { checker.isWordCorrect(any()) } returns false
			coEvery { checker.setUserDictionary(any()) } answers {
				appliedPerChecker[index] = firstArg<Collection<String>>().toSet()
			}
			checker
		}
		coEvery { globalSettingsDatasource.loadSettings() } returns GlobalSettings(
			projectsDirectory = "/projects",
			spellCheckSettings = SpellCheckerSettings(
				enabled = true,
				locale = Locale.forLanguageTag("en"),
			),
		)
		coEvery { globalSettingsDatasource.storeSettings(any()) } just Runs
		coEvery { serverSettingsDatasource.loadServerSettings(any()) } returns null
		every { syncJournal.isServerSynchronized() } returns false
		coEvery { syncJournal.recordIdDeletion(any()) } just Runs
		coEvery { idAllocator.claimNextId() } answers { nextId++ }

		val syncDataDatasource = mockk<SyncDataDatasource>(relaxed = true)
		setupKoin(module {
			scope<ProjectDefScope> {
				scoped { projectDef }
				scoped { syncDataDatasource }
			}
		})

		fileSystem = FakeFileSystem()
		createProject(fileSystem, PROJECT_EMPTY_NAME)

		val toml = createTomlSerializer()
		datasource = EncyclopediaDatasource(
			projectDef = projectDef,
			toml = toml,
			fileSystem = fileSystem,
			externalFileIo = mockk<ExternalFileIo>(),
		)
		encyclopediaRepository = EncyclopediaRepository(
			projectDef = projectDef,
			idAllocator = idAllocator,
			datasource = datasource,
			syncJournal = syncJournal,
		)
		projectDataRepository = ProjectDataRepository(
			ProjectDataDatasource(fileSystem, toml, projectDef),
			projectDef,
		)
		globalSettingsStore = GlobalSettingsStore(globalSettingsDatasource, serverSettingsDatasource)
		spellCheckRepository = SpellCheckRepository(globalSettingsStore, factory)
	}

	private fun createService() = ProjectDictionaryService(
		projectDef = projectDef,
		encyclopediaRepository = encyclopediaRepository,
		projectSpellCheckRepository = ProjectSpellCheckRepository(
			spellCheckRepository = spellCheckRepository,
			globalSettingsStore = globalSettingsStore,
			projectDataRepository = projectDataRepository,
			projectDef = projectDef,
		),
		spellCheckRepository = spellCheckRepository,
	).also { it.initialize() }

	private suspend fun createEntry(
		name: String,
		type: EntryType = EntryType.PERSON,
		aliases: List<String> = emptyList(),
		excludeFromDictionary: Boolean = false,
	) {
		encyclopediaRepository.createEntry(
			name = name,
			type = type,
			text = "text",
			tags = emptySet(),
			imagePath = null,
			aliases = aliases,
			excludeFromDictionary = excludeFromDictionary,
		)
	}

	@Test
	fun `entry names and aliases of all types are tokenized and pushed on start`() = scope.runTest {
		createEntry("Zaltharion", type = EntryType.PERSON, aliases = listOf("Mr. Zal"))
		createEntry("Kastle Rock", type = EntryType.PLACE)
		createEntry("Vorpal Sword", type = EntryType.THING)
		createEntry("The Sundering", type = EntryType.EVENT)
		createEntry("Weirding", type = EntryType.IDEA)

		createService()
		advanceUntilIdle()

		assertEquals(
			setOf(
				"zaltharion", "mr", "zal",
				"kastle", "rock",
				"vorpal", "sword",
				"the", "sundering",
				"weirding",
			),
			appliedPerChecker.last(),
		)
	}

	@Test
	fun `entry changes after start push an updated word set`() = scope.runTest {
		createEntry("Zaltharion")
		createService()
		advanceUntilIdle()

		createEntry("Kastle")
		advanceUntilIdle()

		assertEquals(setOf("zaltharion", "kastle"), appliedPerChecker.last())
	}

	@Test
	fun `text-only entry edits do not recreate the checker`() = scope.runTest {
		createEntry("Zaltharion")
		createService()
		advanceUntilIdle()
		val checkersAfterStart = appliedPerChecker.size

		val def = encyclopediaRepository.ensureEntriesLoaded().single()
		encyclopediaRepository.updateEntry(
			oldEntryDef = def,
			name = def.name,
			text = "new text",
			tags = emptySet(),
			excludeFromDictionary = false,
		)
		advanceUntilIdle()

		assertEquals(checkersAfterStart, appliedPerChecker.size)
	}

	@Test
	fun `disabling the global setting clears the words and re-enabling restores them`() = scope.runTest {
		createEntry("Zaltharion")
		createService()
		advanceUntilIdle()
		assertEquals(setOf("zaltharion"), appliedPerChecker.last())

		globalSettingsStore.updateSettings {
			it.copy(spellCheckSettings = it.spellCheckSettings.copy(includeEncyclopediaNames = false))
		}
		advanceUntilIdle()
		assertEquals(emptySet(), appliedPerChecker.last())

		globalSettingsStore.updateSettings {
			it.copy(spellCheckSettings = it.spellCheckSettings.copy(includeEncyclopediaNames = true))
		}
		advanceUntilIdle()
		assertEquals(setOf("zaltharion"), appliedPerChecker.last())
	}

	@Test
	fun `disabling spell check entirely clears the words and re-enable restores them`() = scope.runTest {
		createEntry("Zaltharion")
		createService()
		advanceUntilIdle()
		assertEquals(setOf("zaltharion"), appliedPerChecker.last())

		globalSettingsStore.updateSettings {
			it.copy(spellCheckSettings = it.spellCheckSettings.copy(enabled = false))
		}
		advanceUntilIdle()

		globalSettingsStore.updateSettings {
			it.copy(spellCheckSettings = it.spellCheckSettings.copy(enabled = true))
		}
		advanceUntilIdle()
		assertEquals(setOf("zaltharion"), appliedPerChecker.last())
	}

	@Test
	fun `disabling the per-project setting clears the words`() = scope.runTest {
		createEntry("Zaltharion")
		createService()
		advanceUntilIdle()
		assertEquals(setOf("zaltharion"), appliedPerChecker.last())

		projectDataRepository.updateData { it.copy(encyclopediaDictionary = false) }
		advanceUntilIdle()

		assertEquals(emptySet(), appliedPerChecker.last())
	}

	@Test
	fun `an excluded entry contributes no words`() = scope.runTest {
		createEntry("Zaltharion")
		createEntry("Secretname", excludeFromDictionary = true, aliases = listOf("Hiddenalias"))

		createService()
		advanceUntilIdle()

		assertEquals(setOf("zaltharion"), appliedPerChecker.last())
	}

	@Test
	fun `closing the project scope clears the words`() = scope.runTest {
		createEntry("Zaltharion")
		val service = createService()
		advanceUntilIdle()
		assertEquals(setOf("zaltharion"), appliedPerChecker.last())

		service.projectScope.scope.close()
		advanceUntilIdle()

		assertEquals(emptySet(), appliedPerChecker.last())
	}

	@Test
	fun `deleting an entry removes its words`() = scope.runTest {
		createEntry("Zaltharion")
		createEntry("Kastle")
		createService()
		advanceUntilIdle()
		assertEquals(setOf("zaltharion", "kastle"), appliedPerChecker.last())

		val def = encyclopediaRepository.ensureEntriesLoaded().first { it.name == "Kastle" }
		encyclopediaRepository.deleteEntry(def)
		advanceUntilIdle()

		assertEquals(setOf("zaltharion"), appliedPerChecker.last())
	}

	@Test
	fun `changes made while the feature is off are reflected on re-enable`() = scope.runTest {
		createEntry("Zaltharion")
		createService()
		advanceUntilIdle()
		assertEquals(setOf("zaltharion"), appliedPerChecker.last())

		globalSettingsStore.updateSettings {
			it.copy(spellCheckSettings = it.spellCheckSettings.copy(includeEncyclopediaNames = false))
		}
		advanceUntilIdle()
		createEntry("Kastle")
		advanceUntilIdle()

		globalSettingsStore.updateSettings {
			it.copy(spellCheckSettings = it.spellCheckSettings.copy(includeEncyclopediaNames = true))
		}
		advanceUntilIdle()

		assertEquals(setOf("zaltharion", "kastle"), appliedPerChecker.last())
	}

	@Test
	fun `entry edits do not re-read other entries from disk`() = scope.runTest {
		createEntry("Zaltharion")
		createEntry("Kastle", aliases = listOf("Rock"))
		createService()
		advanceUntilIdle()
		assertEquals(setOf("zaltharion", "kastle", "rock"), appliedPerChecker.last())

		// Remove Kastle's file behind the datasource's back: a full re-scan would now
		// lose its words, so their survival proves the edit was served from the cache.
		val kastleDef = encyclopediaRepository.ensureEntriesLoaded().first { it.name == "Kastle" }
		fileSystem.delete(datasource.getEntryPath(kastleDef).toOkioPath())

		val zalDef = encyclopediaRepository.ensureEntriesLoaded().first { it.name == "Zaltharion" }
		encyclopediaRepository.updateEntry(
			oldEntryDef = zalDef,
			name = zalDef.name,
			text = "new text",
			tags = emptySet(),
			aliases = listOf("Velcaryn"),
			excludeFromDictionary = false,
		)
		advanceUntilIdle()

		assertEquals(setOf("zaltharion", "velcaryn", "kastle", "rock"), appliedPerChecker.last())
	}

	@Test
	fun `renaming an entry replaces its old tokens`() = scope.runTest {
		createEntry("Zaltharion")
		createService()
		advanceUntilIdle()

		val def = encyclopediaRepository.ensureEntriesLoaded().single()
		encyclopediaRepository.updateEntry(
			oldEntryDef = def,
			name = "Velcaryn",
			text = "text",
			tags = emptySet(),
			excludeFromDictionary = false,
		)
		advanceUntilIdle()

		assertEquals(setOf("velcaryn"), appliedPerChecker.last())
		assertTrue(appliedPerChecker.none { "zaltharion" in it && "velcaryn" in it })
	}
}
