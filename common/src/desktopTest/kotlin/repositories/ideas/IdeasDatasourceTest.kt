package repositories.ideas

import com.darkrockstudios.apps.hammer.base.IdeaId
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettings
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.ideasrepository.IdeasDatasource
import com.darkrockstudios.apps.hammer.common.data.ideasrepository.StoryIdeaCodec
import com.darkrockstudios.apps.hammer.common.data.ideasrepository.idea.StoryIdea
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import utils.BaseTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class IdeasDatasourceTest : BaseTest() {

	private lateinit var ffs: FakeFileSystem
	private lateinit var codec: StoryIdeaCodec
	private lateinit var globalSettingsStore: GlobalSettingsStore
	private lateinit var datasource: IdeasDatasource

	@BeforeEach
	override fun setup() {
		super.setup()
		ffs = FakeFileSystem()
		codec = StoryIdeaCodec(createTomlSerializer())
		globalSettingsStore = mockk()
		every { globalSettingsStore.globalSettings } returns GlobalSettings(projectsDirectory = "/projects")
		setupKoin()
		datasource = IdeasDatasource(ffs, codec, globalSettingsStore)
	}

	private fun makeIdea(content: String, title: String? = null) = StoryIdea(
		id = IdeaId.randomUUID(),
		created = Instant.parse("2026-07-03T14:22:05Z"),
		updated = Instant.parse("2026-07-03T14:22:05Z"),
		title = title,
		content = content,
	)

	@Test
	fun `Ideas directory is created under the projects root`() = runTest {
		datasource.getIdeasDirectory()

		assertTrue(ffs.exists("/projects/.ideas".toPath()))
	}

	@Test
	fun `Stored ideas load back`() = runTest {
		val ideaA = makeIdea("First spark", title = "Spark A")
		val ideaB = makeIdea("Second spark")

		datasource.createIdea(ideaA)
		datasource.createIdea(ideaB)
		val loaded = datasource.loadIdeas()

		assertEquals(setOf(ideaA, ideaB), loaded.toSet())
	}

	@Test
	fun `Malformed idea file is skipped and other ideas still load`() = runTest {
		val good = makeIdea("Good idea")
		datasource.createIdea(good)
		val badPath = datasource.getIdeaPath(IdeaId.randomUUID()).toOkioPath()
		ffs.write(badPath, mustCreate = true) {
			writeUtf8("no front matter, just prose")
		}

		val loaded = datasource.loadIdeas()

		assertEquals(listOf(good), loaded)
	}

	@Test
	fun `Files not matching the idea filename pattern are ignored`() = runTest {
		val dir = datasource.getIdeasDirectory().toOkioPath()
		ffs.write(dir / "README.md", mustCreate = true) { writeUtf8("# hi") }
		ffs.write(dir / "idea-notauuid.md", mustCreate = true) { writeUtf8("junk") }

		val loaded = datasource.loadIdeas()

		assertTrue(loaded.isEmpty())
	}

	@Test
	fun `Update overwrites the stored idea`() = runTest {
		val idea = makeIdea("Original")
		datasource.createIdea(idea)

		val updated = idea.copy(content = "Rewritten")
		datasource.updateIdea(updated)
		val loaded = datasource.loadIdeas()

		assertEquals(listOf(updated), loaded)
	}

	@Test
	fun `Delete removes the idea file`() = runTest {
		val idea = makeIdea("Doomed")
		datasource.createIdea(idea)
		val path = datasource.getIdeaPath(idea.id).toOkioPath()
		assertTrue(ffs.exists(path))

		datasource.deleteIdea(idea.id)

		assertFalse(ffs.exists(path))
		assertNull(datasource.loadIdeas().find { it.id == idea.id })
	}
}
