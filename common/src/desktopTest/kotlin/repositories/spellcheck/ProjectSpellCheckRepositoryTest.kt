package repositories.spellcheck

import app.cash.turbine.test
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectData
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettings
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.globalsettings.SpellCheckerSettings
import com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource.GlobalSettingsDatasource
import com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource.ServerSettingsDatasource
import com.darkrockstudios.apps.hammer.common.data.projectdata.ProjectDataDatasource
import com.darkrockstudios.apps.hammer.common.data.projectdata.ProjectDataRepository
import com.darkrockstudios.apps.hammer.common.data.projectdata.StoredProjectData
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncDataDatasource
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.spellcheck.ProjectSpellCheckRepository
import com.darkrockstudios.apps.hammer.common.spellcheck.SpellCheckRepository
import com.darkrockstudios.apps.hammer.common.util.Locale
import com.darkrockstudios.libs.platformspellchecker.PlatformSpellChecker
import com.darkrockstudios.libs.platformspellchecker.PlatformSpellCheckerFactory
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import utils.BaseTest
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * Classical-style: a real [ProjectSpellCheckRepository] over a real [SpellCheckRepository],
 * [GlobalSettingsStore] and [ProjectDataRepository] (FakeFileSystem-backed). Only the
 * platform spell checker factory and settings persistence are mocked.
 */
class ProjectSpellCheckRepositoryTest : BaseTest() {

	private val projectDef = ProjectDef(
		name = "Test Project",
		path = "/projects/Test Project".toPath().toHPath(),
	)

	private lateinit var globalSettingsDatasource: GlobalSettingsDatasource
	private lateinit var serverSettingsDatasource: ServerSettingsDatasource
	private lateinit var factory: PlatformSpellCheckerFactory
	private lateinit var checker: PlatformSpellChecker
	private lateinit var fileSystem: FakeFileSystem
	private lateinit var projectDataRepository: ProjectDataRepository
	private lateinit var projectDataDatasource: ProjectDataDatasource

	@BeforeEach
	override fun setup() {
		super.setup()
		globalSettingsDatasource = mockk()
		serverSettingsDatasource = mockk()
		factory = mockk()
		checker = mockk()

		every { factory.hasLanguage(any()) } returns true
		coEvery { factory.createSpellChecker(any()) } returns checker
		coEvery { globalSettingsDatasource.storeSettings(any()) } just Runs
		coEvery { serverSettingsDatasource.loadServerSettings(any()) } returns null

		fileSystem = FakeFileSystem()
		fileSystem.createDirectories("/projects/Test Project".toPath())

		val syncDataDatasource = mockk<SyncDataDatasource>(relaxed = true)
		setupKoin(module {
			scope<ProjectDefScope> {
				scoped { projectDef }
				scoped { syncDataDatasource }
			}
		})

		projectDataDatasource = ProjectDataDatasource(fileSystem, createTomlSerializer(), projectDef)
		projectDataRepository = ProjectDataRepository(projectDataDatasource, projectDef)
	}

	private fun settingsStore(dictionary: Locale): GlobalSettingsStore {
		coEvery { globalSettingsDatasource.loadSettings() } returns GlobalSettings(
			projectsDirectory = "/projects",
			spellCheckSettings = SpellCheckerSettings(enabled = true, locale = dictionary),
		)
		return GlobalSettingsStore(globalSettingsDatasource, serverSettingsDatasource)
	}

	private fun repository(store: GlobalSettingsStore) = ProjectSpellCheckRepository(
		spellCheckRepository = SpellCheckRepository(store, factory),
		globalSettingsStore = store,
		projectDataRepository = projectDataRepository,
		projectDef = projectDef,
	)

	@Test
	fun `unset project language passes the global dictionary through`() = scope.runTest {
		val repo = repository(settingsStore(Locale.forLanguageTag("en-US")))

		repo.dictionaryFlow.test {
			assertSame(checker, awaitItem())
			cancelAndConsumeRemainingEvents()
		}
	}

	@Test
	fun `matching project language passes the dictionary through`() = scope.runTest {
		projectDataDatasource.save(StoredProjectData(ProjectData(language = "en"), null))
		val repo = repository(settingsStore(Locale.forLanguageTag("en-US")))

		repo.dictionaryFlow.test {
			assertSame(checker, awaitItem())
			cancelAndConsumeRemainingEvents()
		}
	}

	@Test
	fun `mismatched project language withholds the dictionary`() = scope.runTest {
		projectDataDatasource.save(StoredProjectData(ProjectData(language = "fr"), null))
		val repo = repository(settingsStore(Locale.forLanguageTag("en-US")))

		repo.dictionaryFlow.test {
			assertEquals(null, awaitItem())
			cancelAndConsumeRemainingEvents()
		}
	}

	@Test
	fun `changing the dictionary to a matching language re-enables spell check`() = scope.runTest {
		projectDataDatasource.save(StoredProjectData(ProjectData(language = "fr"), null))
		val store = settingsStore(Locale.forLanguageTag("en-US"))
		val repo = repository(store)

		repo.dictionaryFlow.test {
			assertEquals(null, awaitItem())

			store.updateSettings { settings ->
				settings.copy(
					spellCheckSettings = settings.spellCheckSettings.copy(
						locale = Locale.forLanguageTag("fr")
					)
				)
			}

			assertSame(checker, awaitItem())
			cancelAndConsumeRemainingEvents()
		}
	}

	@Test
	fun `setting the project language to a mismatch disables spell check`() = scope.runTest {
		val repo = repository(settingsStore(Locale.forLanguageTag("en-US")))

		repo.dictionaryFlow.test {
			assertSame(checker, awaitItem())

			projectDataRepository.updateData { it.copy(language = "de") }

			assertEquals(null, awaitItem())
			cancelAndConsumeRemainingEvents()
		}
	}

	@Test
	fun `spellCheckAllowed reflects the language gate`() = scope.runTest {
		projectDataDatasource.save(StoredProjectData(ProjectData(language = "fr"), null))
		val repo = repository(settingsStore(Locale.forLanguageTag("en-US")))

		repo.spellCheckAllowed.test {
			assertEquals(false, awaitItem())

			projectDataRepository.updateData { it.copy(language = "en") }

			assertEquals(true, awaitItem())
			cancelAndConsumeRemainingEvents()
		}
	}
}
