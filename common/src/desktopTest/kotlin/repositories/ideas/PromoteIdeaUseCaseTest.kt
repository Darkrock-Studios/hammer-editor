package repositories.ideas

import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettings
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.ideasrepository.IdeasDatasource
import com.darkrockstudios.apps.hammer.common.data.ideasrepository.IdeasRepository
import com.darkrockstudios.apps.hammer.common.data.ideasrepository.PromoteIdeaUseCase
import com.darkrockstudios.apps.hammer.base.http.createJsonSerializer
import com.darkrockstudios.apps.hammer.common.data.ideasrepository.StoryIdeaCodec
import com.darkrockstudios.apps.hammer.common.data.isSuccess
import com.darkrockstudios.apps.hammer.common.data.sync.ideassync.IdeasSyncDatasource
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NotesDatasource
import com.darkrockstudios.apps.hammer.common.data.notesrepository.note.NoteContainer
import com.darkrockstudios.apps.hammer.common.data.projectdata.ProjectDataDatasource
import com.darkrockstudios.apps.hammer.common.data.projectdata.StoredProjectData
import com.darkrockstudios.apps.hammer.common.data.projectmetadata.ProjectMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import com.darkrockstudios.apps.hammer.common.util.DeviceLocaleResolver
import com.darkrockstudios.apps.hammer.common.util.Locale
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.peanuuutz.tomlkt.Toml
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import utils.BaseTest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class PromoteIdeaUseCaseTest : BaseTest() {

	private lateinit var ffs: FakeFileSystem
	private lateinit var toml: Toml
	private lateinit var projectsRepository: ProjectsRepository
	private lateinit var ideasRepository: IdeasRepository
	private lateinit var promoteIdea: PromoteIdeaUseCase

	private class FixedClock(var time: Instant) : Clock {
		override fun now(): Instant = time
	}

	@BeforeEach
	override fun setup() {
		super.setup()
		ffs = FakeFileSystem()
		toml = createTomlSerializer()

		val globalSettingsStore = mockk<GlobalSettingsStore>()
		every { globalSettingsStore.globalSettings } returns GlobalSettings(projectsDirectory = "/projects")
		every { globalSettingsStore.globalSettingsUpdates } returns MutableSharedFlow()

		setupKoin()

		val deviceLocaleResolver = mockk<DeviceLocaleResolver>()
		every { deviceLocaleResolver.getCurrentLocale() } returns Locale.forLanguage("en", "US")
		projectsRepository = ProjectsRepository(
			fileSystem = ffs,
			globalSettingsStore = globalSettingsStore,
			projectsMetadataDatasource = ProjectMetadataDatasource(ffs, toml),
			toml = toml,
			deviceLocaleResolver = deviceLocaleResolver,
		)
		val ideasDatasource = IdeasDatasource(ffs, StoryIdeaCodec(toml), globalSettingsStore)
		ideasRepository = IdeasRepository(
			ideasDatasource = ideasDatasource,
			ideasSyncDatasource = IdeasSyncDatasource(ffs, createJsonSerializer(), ideasDatasource),
			clock = FixedClock(Instant.parse("2026-07-04T12:00:00Z")),
		)
		promoteIdea = PromoteIdeaUseCase(
			projectsRepository = projectsRepository,
			ideasRepository = ideasRepository,
			fileSystem = ffs,
			toml = toml,
			clock = FixedClock(Instant.parse("2026-07-04T12:00:00Z")),
		)
	}

	@Test
	fun `Promotion creates a project seeded with the idea as its first note`() = runTest {
		advanceUntilIdle()
		val ideaResult = ideasRepository.createIdea(
			content = "What if the light itself was the inheritance...",
			title = "The Lighthouse Keeper's Daughter",
			tags = setOf("gothic", "coastal"),
		)
		assertTrue(isSuccess(ideaResult))
		val idea = ideaResult.data

		val result = promoteIdea(idea.id)

		assertTrue(isSuccess(result))
		val projectDef = result.data
		assertEquals("The Lighthouse Keeper's Daughter", projectDef.name)
		assertTrue(ffs.exists(projectDef.path.toOkioPath()))

		val notePath = NotesDatasource.getNotePath(1, projectDef, ffs).toOkioPath()
		assertTrue(ffs.exists(notePath))
		val note: NoteContainer = toml.decodeFromString(
			NoteContainer.serializer(),
			ffs.read(notePath) { readUtf8() },
		)
		assertEquals(idea.content, note.note.content)
		assertEquals(setOf("gothic", "coastal"), note.note.tags)

		val storedProjectData: StoredProjectData = toml.decodeFromString(
			StoredProjectData.serializer(),
			ffs.read(projectDef.path.toOkioPath() / ProjectDataDatasource.FILENAME) { readUtf8() },
		)
		assertEquals(setOf("gothic", "coastal"), storedProjectData.data.tags)

		val promoted = ideasRepository.getIdeaById(idea.id)
		assertNotNull(promoted?.promoted)
	}

	@Test
	fun `Promotion derives the project name from the first content line when untitled`() = runTest {
		advanceUntilIdle()
		val idea = ideasRepository.createIdea(
			content = "## A courier of dreams\n\nShe delivers letters to sleepers.",
		).let { assertTrue(isSuccess(it)); it.data }

		val result = promoteIdea(idea.id)

		assertTrue(isSuccess(result))
		assertEquals("A courier of dreams", result.data.name)
	}

	@Test
	fun `Promotion suffixes the project name when it already exists`() = runTest {
		advanceUntilIdle()
		projectsRepository.createProject("Tides", seedDefaultLanguage = true).let { assertTrue(isSuccess(it)) }
		val idea = ideasRepository.createIdea(content = "waves", title = "Tides")
			.let { assertTrue(isSuccess(it)); it.data }

		val result = promoteIdea(idea.id)

		assertTrue(isSuccess(result))
		assertEquals("Tides 2", result.data.name)
		assertTrue(ffs.exists("/projects/Tides 2".toPath()))
	}
}
