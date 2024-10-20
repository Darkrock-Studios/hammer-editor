package repositories.scenedraft

import PROJECT_2_NAME
import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import utils.BaseTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
	fun `Find Drafts for a scene with drafts`() = runTest {
		createProject(ffs, PROJECT_2_NAME)
		val sceneId = 1

		val repo = createRepository()
		val drafts = repo.findDrafts(sceneId)

		assertEquals(2, drafts.size)
	}

	@Test
	fun `Find Drafts for a scene with no drafts`() = runTest {
		createProject(ffs, PROJECT_2_NAME)
		val sceneId = 7

		val repo = createRepository()
		val drafts = repo.findDrafts(sceneId)

		assertEquals(0, drafts.size)
	}

	@Test
	fun `Get Draft Def for a draft`() = runTest {
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
	fun `Fail Get Draft Def because it doesn't exist`() = runTest {
		val draftId = 7
		createProject(ffs, PROJECT_2_NAME)

		val repo = createRepository()
		val draftDef = repo.getDraftDef(draftId)

		assertNull(draftDef)
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

	@Test
	fun `Insert draft`() = runTest {
		createProject(ffs, PROJECT_2_NAME)

		val sceneItem = ApiProjectEntity.SceneDraftEntity(
			id = 12,
			sceneId = 1,
			name = "Scene 3",
			created = Instant.fromEpochSeconds(1234567),
			content = "Some scene content text",
		)

		val repo = createRepository()
		val draftDef = repo.insertSyncDraft(sceneItem)

		assertNotNull(draftDef)
		assertEquals(
			DraftDef(
				id = sceneItem.id,
				sceneId = sceneItem.sceneId,
				draftTimestamp = sceneItem.created,
				draftName = sceneItem.name,
			),
			draftDef
		)

		val draftFile = datasource.getDraftPath(draftDef).toOkioPath()
		ffs.exists(draftFile)
		val draftContent = ffs.read(draftFile) {
			readUtf8()
		}
		assertEquals(sceneItem.content, draftContent)
	}

	@Test
	fun `Delete a draft`() = runTest {
		createProject(ffs, PROJECT_2_NAME)
		val draftDef = DraftDef(
			id = 9,
			sceneId = 1,
			draftTimestamp = Instant.fromEpochSeconds(1729285670),
			draftName = "First Draft"
		)

		val repo = createRepository()

		val draftFile = datasource.getDraftPath(draftDef).toOkioPath()
		assertTrue(ffs.exists(draftFile))

		val deleted = repo.deleteDraft(9)
		assertTrue(deleted)

		assertFalse(ffs.exists(draftFile))
	}

	@Test
	fun `Load a draft`() = runTest {
		createProject(ffs, PROJECT_2_NAME)

		val sceneItem = SceneItem(
			projectDef = projectDef,
			type = SceneItem.Type.Scene,
			id = 9,
			name = "Scene 3",
			order = 3,
		)
		val draftDef = DraftDef(
			id = 9,
			sceneId = 1,
			draftTimestamp = Instant.fromEpochSeconds(1729285670),
			draftName = "First Draft"
		)

		val repo = createRepository()

		val draftFile = datasource.getDraftPath(draftDef).toOkioPath()
		assertTrue(ffs.exists(draftFile))

		val draft = repo.loadDraft(sceneItem, draftDef)
		assertNotNull(draft)

		assertEquals(sceneItem, draft?.scene)
		assertEquals("First draft content", draft?.markdown)
	}

	@Test
	fun `Load a draft content`() = runTest {
		createProject(ffs, PROJECT_2_NAME)

		val draftDef = DraftDef(
			id = 9,
			sceneId = 1,
			draftTimestamp = Instant.fromEpochSeconds(1729285670),
			draftName = "First Draft"
		)

		val repo = createRepository()

		val draftFile = datasource.getDraftPath(draftDef).toOkioPath()
		assertTrue(ffs.exists(draftFile))

		val draftContent = repo.loadDraftContent(draftDef)
		assertNotNull(draftContent)
		assertEquals("First draft content", draftContent)
	}

	@Test
	fun `ReId Draft`() = runTest {
		createProject(ffs, PROJECT_2_NAME)

		val oldDraftDef = DraftDef(
			id = 11,
			sceneId = 6,
			draftTimestamp = Instant.fromEpochSeconds(1729186670),
			draftName = "First Draft"
		)
		val newDraftDef = DraftDef(
			id = 15,
			sceneId = 6,
			draftTimestamp = Instant.fromEpochSeconds(1729186670),
			draftName = "First Draft"
		)

		val repo = createRepository()

		repo.reIdDraft(oldId = oldDraftDef.id, newId = newDraftDef.id)

		val oldDraftFile = datasource.getDraftPath(oldDraftDef).toOkioPath()
		assertFalse(ffs.exists(oldDraftFile))

		val newFraftFile = datasource.getDraftPath(newDraftDef).toOkioPath()
		assertTrue(ffs.exists(newFraftFile))
	}

	@Test
	fun `ReId Scene`() = runTest {
		createProject(ffs, PROJECT_2_NAME)

		val oldDraftDef = DraftDef(
			id = 11,
			sceneId = 6,
			draftTimestamp = Instant.fromEpochSeconds(1729186670),
			draftName = "First Draft"
		)
		val newDraftDef = DraftDef(
			id = 11,
			sceneId = 15,
			draftTimestamp = Instant.fromEpochSeconds(1729186670),
			draftName = "First Draft"
		)

		val repo = createRepository()

		repo.reIdScene(oldId = oldDraftDef.sceneId, newId = newDraftDef.sceneId)

		val oldDraftFile = datasource.getDraftPath(oldDraftDef).toOkioPath()
		assertFalse(ffs.exists(oldDraftFile))

		val newFraftFile = datasource.getDraftPath(newDraftDef).toOkioPath()
		assertTrue(ffs.exists(newFraftFile))
	}
}
