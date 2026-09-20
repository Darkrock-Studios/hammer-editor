package repositories.scenedraft

import PROJECT_2_NAME
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.drafts.DraftDef
import com.darkrockstudios.apps.hammer.common.data.drafts.SceneDraftRepository
import com.darkrockstudios.apps.hammer.common.data.drafts.SceneDraftsDatasource
import com.darkrockstudios.apps.hammer.common.data.id.IdAllocator
import com.darkrockstudios.apps.hammer.common.data.migrator.Migration2_3
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneContentRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneDatasource
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import createProject
import getProjectDef
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import utils.BaseTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Covers the widened draft-name character set (data v3): what the repository accepts, what lands
 * on disk, and that a name survives the round trip through the filename.
 */
class SceneDraftNameTest : BaseTest() {

	private val projectDef = getProjectDef(PROJECT_2_NAME)

	private lateinit var ffs: FakeFileSystem
	private lateinit var datasource: SceneDraftsDatasource
	private lateinit var sceneContentRepository: SceneContentRepository
	private lateinit var idAllocator: IdAllocator
	private lateinit var clock: Clock

	private val scene = SceneItem(
		projectDef = projectDef,
		type = SceneItem.Type.Scene,
		id = 1,
		name = "Scene",
		order = 0,
	)

	@BeforeEach
	override fun setup() {
		super.setup()

		sceneContentRepository = mockk()
		idAllocator = mockk()
		clock = mockk()
		ffs = FakeFileSystem()

		setupKoin(module {
			single { idAllocator }
			single { clock }
		})

		createProject(ffs, PROJECT_2_NAME)
		every { clock.now() } returns Instant.fromEpochSeconds(1_700_000_000)
		coEvery { sceneContentRepository.getCurrentSceneContent(any()) } returns "content"
	}

	private fun createRepository(nextId: Int = 900): SceneDraftRepository {
		coEvery { idAllocator.claimNextId() } returns nextId
		datasource = SceneDraftsDatasource(ffs, SceneDatasource(projectDef, ffs))
		return SceneDraftRepository(projectDef, sceneContentRepository, datasource, clock)
	}

	@Test
	fun `names with punctuation that used to be rejected are now accepted`() = runTest {
		val names = listOf(
			"Act 2, Take 3",
			"Draft - final?",
			"Editor's cut (v2)",
			"Réécriture",
			"Chapter 3: The Fall",
			"Ready!",
		)

		names.forEachIndexed { index, name ->
			val repo = createRepository(nextId = 900 + index)
			assertNotNull(repo.saveDraft(scene, name), "'$name' should be a legal draft name")
		}
	}

	@Test
	fun `the reserved filename delimiter is still rejected`() = runTest {
		val repo = createRepository()

		assertNull(repo.saveDraft(scene, "bad~name"))
	}

	@Test
	fun `a blank name is still rejected`() = runTest {
		val repo = createRepository()

		assertNull(repo.saveDraft(scene, "   "))
	}

	@Test
	fun `a name with OS-forbidden characters is stored under a safe filename and round-trips`() =
		runTest {
			val repo = createRepository()
			val name = "Chapter 3: The Fall?"

			val def = repo.saveDraft(scene, name)
			assertNotNull(def)

			val path = datasource.getDraftPath(def).toOkioPath()
			assertTrue(ffs.exists(path))
			assertFalse(path.name.contains(':'), "Filename must not contain a forbidden char")
			assertFalse(path.name.contains('?'), "Filename must not contain a forbidden char")

			assertEquals(name, repo.getDraftDef(def.id)?.draftName)
		}

	@Test
	fun `a draft stored under the legacy filename is still listed, loaded and deleted`() = runTest {
		val repo = createRepository()
		val draftsDir = datasource.getDraftsDirectory().toOkioPath() / "1"
		ffs.createDirectories(draftsDir)
		val legacyPath = draftsDir / "1-901-Old Draft-1729285670.md"
		ffs.write(legacyPath) { writeUtf8("legacy content") }

		val def = repo.getDraftDef(901)
		assertEquals(
			DraftDef(
				id = 901,
				sceneId = 1,
				draftTimestamp = Instant.fromEpochSeconds(1729285670),
				draftName = "Old Draft",
			),
			def,
		)
		assertEquals("legacy content", repo.loadDraftContent(def!!))

		assertTrue(repo.deleteDraft(901))
		assertFalse(ffs.exists(legacyPath), "The legacy file must actually be deleted")
	}

	@Test
	fun `a legacy draft whose name has a trailing space survives migration`() = runTest {
		val repo = createRepository()
		val draftsDir = datasource.getDraftsDirectory().toOkioPath() / "1"
		ffs.createDirectories(draftsDir)
		ffs.write(draftsDir / "1-902-My Draft -1729285670.md") { writeUtf8("legacy content") }

		Migration2_3(ffs).migrate(projectDef)

		val def = repo.getDraftDef(902)
		assertNotNull(def)
		assertEquals("My Draft", def.draftName, "The name must be canonicalised, not left padded")
		assertEquals("legacy content", repo.loadDraftContent(def))

		assertTrue(repo.deleteDraft(902))
		assertFalse(
			ffs.list(draftsDir).any { it.name.contains("902") },
			"Delete must actually remove the file, not leave a ghost draft",
		)
	}

	@Test
	fun `a trailing space in a draft name is accepted and stored trimmed`() = runTest {
		val repo = createRepository()

		val def = repo.saveDraft(scene, "My Draft ")

		assertNotNull(def)
		assertEquals("My Draft", def.draftName)
		assertTrue(ffs.exists(datasource.getDraftPath(def).toOkioPath()))
	}

	@Test
	fun `a new draft is written in the current filename format`() = runTest {
		val repo = createRepository()

		val def = repo.saveDraft(scene, "Act 2, Take 3")
		assertNotNull(def)

		val path = datasource.getDraftPath(def).toOkioPath()
		assertEquals("1~900~Act 2, Take 3~1700000000.md", path.name)
	}
}
