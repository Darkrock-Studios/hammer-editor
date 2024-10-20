package repositories.scenedraft

import PROJECT_2_NAME
import com.darkrockstudios.apps.hammer.common.data.SceneBuffer
import com.darkrockstudios.apps.hammer.common.data.SceneContent
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.UpdateSource
import com.darkrockstudios.apps.hammer.common.data.drafts.DraftDef
import com.darkrockstudios.apps.hammer.common.data.drafts.SceneDraftRepository
import com.darkrockstudios.apps.hammer.common.data.drafts.SceneDraftsDatasource
import com.darkrockstudios.apps.hammer.common.data.id.IdRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneDatasource
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorRepositoryOkio
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import createProject
import getProjectDef
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import net.peanuuutz.tomlkt.Toml
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import utils.BaseTest
import kotlin.test.assertEquals

class SceneDraftRepositoryTest : BaseTest() {

	private val projectDef = getProjectDef(PROJECT_2_NAME)

	private lateinit var sceneEditorRepository: SceneEditorRepository
	private lateinit var ffs: FakeFileSystem
	private lateinit var toml: Toml
	private lateinit var datasource: SceneDraftsDatasource
	private lateinit var sceneDatasource: SceneDatasource
	private lateinit var idRepository: IdRepository
	private lateinit var clock: Clock

	@BeforeEach
	override fun setup() {
		super.setup()

		sceneEditorRepository = mockk<SceneEditorRepositoryOkio>()
		every { sceneEditorRepository.getSceneDirectory() } answers {
			SceneDatasource.getSceneDirectory(projectDef, ffs)
		}

		idRepository = mockk<IdRepository>()
		clock = mockk<Clock>()

		ffs = FakeFileSystem()
		toml = createTomlSerializer()

		setupKoin(
			module {
				single { idRepository }
				single { clock }
			}
		)
	}

	private fun createRepository(): SceneDraftRepository {
		sceneDatasource = SceneDatasource(projectDef, ffs)
		datasource = SceneDraftsDatasource(ffs, sceneDatasource)
		return SceneDraftRepository(projectDef, sceneEditorRepository, datasource, clock)
	}

	@Test
	fun `Initialize scene draft repository`() {
		val repo = createRepository()
	}

	@Test
	fun `Get All Drafts`() = runTest {
		createProject(ffs, PROJECT_2_NAME)

		val repo = createRepository()
		val drafts = repo.getAllDrafts()

		assertEquals(3, drafts.size)
	}

	@Test
	fun `Get Draft`() = runTest {
		val draftId = 10
		createProject(ffs, PROJECT_2_NAME)

		val repo = createRepository()
		val draftDef = repo.getDraftDef(draftId)

		val expected = DraftDef(
			id = 10,
			sceneId = 1,
			draftTimestamp = Instant.fromEpochSeconds(1729286670),
			draftName = "Second Draft"
		)

		assertEquals(expected, draftDef)
	}

	@Test
	fun `Get Scene Ids with Drafts`() = runTest {
		createProject(ffs, PROJECT_2_NAME)

		val repo = createRepository()
		val sceneIds = repo.getSceneIdsThatHaveDrafts()

		assertEquals(listOf(1, 6), sceneIds)
	}

	@Test
	fun `Save new draft with invalid name`() = runTest {
		createProject(ffs, PROJECT_2_NAME)

		val sceneId = 3
		val sceneItem = SceneItem(
			projectDef = projectDef,
			type = SceneItem.Type.Scene,
			id = sceneId,
			name = "Scene 3",
			order = 3,
		)

		val repo = createRepository()
		val draftDef = repo.saveDraft(sceneItem, "")
		assertNull(draftDef)
	}

	@Test
	fun `Save new draft that has a scene buffer in memory`() = runTest {
		createProject(ffs, PROJECT_2_NAME)

		val sceneId = 3
		val sceneItem = SceneItem(
			projectDef = projectDef,
			type = SceneItem.Type.Scene,
			id = sceneId,
			name = "Scene 3",
			order = 3,
		)
		val buffer = SceneBuffer(
			content = SceneContent(sceneItem, "Some scene content text", null),
			dirty = false,
			source = UpdateSource.Repository,
		)
		val fakeNow = Instant.fromEpochSeconds(1729286670)
		val draftName = "New Draft"
		val draftId = 11

		coEvery { idRepository.claimNextId() } returns draftId
		coEvery { sceneEditorRepository.getSceneBuffer(any<Int>()) } returns buffer
		every { clock.now() } returns fakeNow

		val repo = createRepository()
		val draftDef = repo.saveDraft(sceneItem, draftName)

		assertNotNull(draftDef)
		assertEquals(
			DraftDef(
				id = draftId,
				sceneId = sceneId,
				draftTimestamp = fakeNow,
				draftName = draftName
			), draftDef
		)

		val draftFile = datasource.getDraftPath(draftDef!!).toOkioPath()
		ffs.exists(draftFile)
		val draftContent = ffs.read(draftFile) {
			readUtf8()
		}
		assertEquals(buffer.content.markdown, draftContent)
	}

	@Test
	fun `Save new draft that has to load from disk`() = runTest {
		createProject(ffs, PROJECT_2_NAME)

		val sceneId = 3
		val sceneItem = SceneItem(
			projectDef = projectDef,
			type = SceneItem.Type.Scene,
			id = sceneId,
			name = "Scene 3",
			order = 3,
		)
		val buffer = SceneBuffer(
			content = SceneContent(sceneItem, "Some scene content text", null),
			dirty = false,
			source = UpdateSource.Repository,
		)
		val fakeNow = Instant.fromEpochSeconds(1729286670)
		val draftName = "New Draft"
		val draftId = 11

		coEvery { idRepository.claimNextId() } returns draftId
		coEvery { sceneEditorRepository.getSceneBuffer(any<Int>()) } returns null
		coEvery { sceneEditorRepository.loadSceneBuffer(any()) } returns buffer
		every { clock.now() } returns fakeNow

		val repo = createRepository()
		val draftDef = repo.saveDraft(sceneItem, draftName)

		assertNotNull(draftDef)
		assertEquals(
			DraftDef(
				id = draftId,
				sceneId = sceneId,
				draftTimestamp = fakeNow,
				draftName = draftName
			), draftDef
		)

		val draftFile = datasource.getDraftPath(draftDef!!).toOkioPath()
		ffs.exists(draftFile)
		val draftContent = ffs.read(draftFile) {
			readUtf8()
		}
		assertEquals(buffer.content.markdown, draftContent)
	}
}
