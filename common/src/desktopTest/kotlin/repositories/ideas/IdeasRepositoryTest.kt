package repositories.ideas

import app.cash.turbine.test
import com.darkrockstudios.apps.hammer.base.IdeaId
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettings
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.ideasrepository.IdeaError
import com.darkrockstudios.apps.hammer.common.data.ideasrepository.IdeasDatasource
import com.darkrockstudios.apps.hammer.common.data.ideasrepository.IdeasRepository
import com.darkrockstudios.apps.hammer.common.data.ideasrepository.IdeasRepository.Companion.MAX_TAG_SIZE
import com.darkrockstudios.apps.hammer.common.data.ideasrepository.InvalidIdea
import com.darkrockstudios.apps.hammer.common.data.ideasrepository.StoryIdeaCodec
import com.darkrockstudios.apps.hammer.common.data.ideasrepository.idea.StoryIdea
import com.darkrockstudios.apps.hammer.common.data.isFailure
import com.darkrockstudios.apps.hammer.common.data.isSuccess
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import utils.BaseTest
import java.util.stream.Stream
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class IdeasRepositoryTest : BaseTest() {

	private lateinit var ffs: FakeFileSystem
	private lateinit var globalSettingsStore: GlobalSettingsStore
	private lateinit var datasource: IdeasDatasource
	private lateinit var clock: FixedClock

	private class FixedClock(var time: Instant) : Clock {
		override fun now(): Instant = time
	}

	@BeforeEach
	override fun setup() {
		super.setup()
		ffs = FakeFileSystem()
		globalSettingsStore = mockk()
		every { globalSettingsStore.globalSettings } returns GlobalSettings(projectsDirectory = "/projects")
		clock = FixedClock(Instant.parse("2026-07-03T14:22:05Z"))
		setupKoin()
	}

	private fun createRepository(): IdeasRepository {
		datasource = IdeasDatasource(ffs, StoryIdeaCodec(createTomlSerializer()), globalSettingsStore)
		return IdeasRepository(datasource, clock)
	}

	private suspend fun IdeasRepository.createIdeaOrFail(content: String): StoryIdea {
		val result = createIdea(content = content)
		assertTrue(isSuccess(result))
		return result.data
	}

	@Test
	fun `Ideas on disk load on init`() = runTest {
		datasource = IdeasDatasource(ffs, StoryIdeaCodec(createTomlSerializer()), globalSettingsStore)
		val existing = StoryIdea(
			id = IdeaId.randomUUID(),
			created = clock.now(),
			updated = clock.now(),
			content = "Pre-existing idea",
		)
		datasource.createIdea(existing)

		val repo = IdeasRepository(datasource, clock)

		repo.ideasFlow.test {
			assertEquals(listOf(existing), awaitItem())
		}
	}

	@Test
	fun `Create idea stores the file and updates the flow`() = runTest {
		val repo = createRepository()
		advanceUntilIdle()

		val result = repo.createIdea(
			content = "A story about tides",
			title = "Tides",
			tags = setOf("  ocean ", "#moon"),
		)

		assertTrue(isSuccess(result))
		val idea = result.data
		assertEquals("Tides", idea.title)
		assertEquals(setOf("ocean", "moon"), idea.tags)
		assertEquals(clock.now(), idea.created)
		assertEquals(clock.now(), idea.updated)
		assertNull(idea.promoted)
		assertNull(idea.archived)
		assertTrue(ffs.exists(datasource.getIdeaPath(idea.id).toOkioPath()))

		repo.ideasFlow.test {
			assertEquals(listOf(idea), awaitItem())
		}
	}

	@Test
	fun `Blank title is normalized to null`() = runTest {
		val repo = createRepository()
		advanceUntilIdle()

		val result = repo.createIdea(content = "Untitled spark", title = "   ")

		assertTrue(isSuccess(result))
		assertNull(result.data.title)
	}

	@ParameterizedTest
	@MethodSource("provideCreateFailureTestData")
	fun `Create idea fails validation`(content: String, tags: Set<String>, error: IdeaError) = runTest {
		val repo = createRepository()
		advanceUntilIdle()

		val result = repo.createIdea(content = content, tags = tags)

		assertTrue(isFailure(result))
		assertEquals(error, (result.exception as InvalidIdea).error)
		repo.ideasFlow.test {
			assertTrue(awaitItem().isEmpty())
		}
	}

	@Test
	fun `Update idea stamps updated and persists`() = runTest {
		val repo = createRepository()
		advanceUntilIdle()
		val created = repo.createIdeaOrFail("v1")

		clock.time = Instant.parse("2026-07-04T08:00:00Z")
		val result = repo.updateIdea(created.copy(content = "v2"))

		assertTrue(isSuccess(result))
		assertEquals("v2", result.data.content)
		assertEquals(Instant.parse("2026-07-04T08:00:00Z"), result.data.updated)
		assertEquals(created.created, result.data.created)

		assertEquals(listOf(result.data), datasource.loadIdeas())
		repo.ideasFlow.test {
			assertEquals(listOf(result.data), awaitItem())
		}
	}

	@Test
	fun `Delete idea removes file and flow entry`() = runTest {
		val repo = createRepository()
		advanceUntilIdle()
		val created = repo.createIdeaOrFail("doomed")
		val path = datasource.getIdeaPath(created.id).toOkioPath()
		assertTrue(ffs.exists(path))

		repo.deleteIdea(created.id)

		assertFalse(ffs.exists(path))
		repo.ideasFlow.test {
			assertTrue(awaitItem().isEmpty())
		}
	}

	@Test
	fun `Archive and unarchive set and clear the timestamp`() = runTest {
		val repo = createRepository()
		advanceUntilIdle()
		val created = repo.createIdeaOrFail("keep me")

		clock.time = Instant.parse("2026-07-05T10:00:00Z")
		val archived = repo.archiveIdea(created.id)
		assertTrue(isSuccess(archived))
		assertEquals(clock.now(), archived.data.archived)

		val unarchived = repo.unarchiveIdea(created.id)
		assertTrue(isSuccess(unarchived))
		assertNull(unarchived.data.archived)
	}

	@Test
	fun `Mark promoted sets the timestamp`() = runTest {
		val repo = createRepository()
		advanceUntilIdle()
		val created = repo.createIdeaOrFail("promote me")

		clock.time = Instant.parse("2026-07-06T12:00:00Z")
		val result = repo.markPromoted(created.id)

		assertTrue(isSuccess(result))
		assertEquals(clock.now(), result.data.promoted)
		assertEquals(clock.now(), datasource.loadIdeas().single().promoted)
	}

	@Test
	fun `Get idea by id`() = runTest {
		val repo = createRepository()
		advanceUntilIdle()
		val created = repo.createIdeaOrFail("find me")

		val found = repo.getIdeaById(created.id)

		assertNotNull(found)
		assertEquals("find me", found.content)
	}

	@Test
	fun `Archive of unknown id fails`() = runTest {
		val repo = createRepository()
		advanceUntilIdle()

		val result = repo.archiveIdea(IdeaId.randomUUID())

		assertTrue(isFailure(result))
	}

	companion object {
		@JvmStatic
		fun provideCreateFailureTestData(): Stream<Arguments> {
			return Stream.of(
				Arguments.of("", emptySet<String>(), IdeaError.EMPTY),
				Arguments.of("   ", emptySet<String>(), IdeaError.EMPTY),
				Arguments.of("x".repeat(StoryIdea.MAX_CONTENT_LENGTH + 1), emptySet<String>(), IdeaError.TOO_LONG),
				Arguments.of("fine", setOf("y".repeat(MAX_TAG_SIZE + 1)), IdeaError.TAG_TOO_LONG),
			)
		}
	}
}
