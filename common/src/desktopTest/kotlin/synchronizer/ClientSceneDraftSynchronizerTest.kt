package synchronizer

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.EntityType
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.drafts.DraftDef
import com.darkrockstudios.apps.hammer.common.data.drafts.SceneDraftRepository
import com.darkrockstudios.apps.hammer.common.data.projectmetadata.ProjectMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncLogLevel
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncLogMessage
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.synchronizers.ClientSceneDraftSynchronizer
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.server.ServerProjectApi
import com.darkrockstudios.apps.hammer.common.util.StrRes
import io.mockk.MockKAnnotations
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import utils.BaseTest
import utils.TestStrRes
import kotlin.test.*
import kotlin.time.Instant

class ClientSceneDraftSynchronizerTest : BaseTest() {

	private val projectDef = ProjectDef(name = "Test", path = HPath("/projects/Test", "Test", false))
	private val strRes: StrRes = TestStrRes()

	@MockK
	private lateinit var serverProjectApi: ServerProjectApi

	@MockK
	private lateinit var projectMetadataDatasource: ProjectMetadataDatasource

	@MockK(relaxed = true)
	private lateinit var sceneDraftRepository: SceneDraftRepository

	private fun draftDef(id: Int, sceneId: Int = 1, name: String = "draft") =
		DraftDef(id = id, sceneId = sceneId, draftTimestamp = Instant.fromEpochSeconds(100), draftName = name)

	@BeforeEach
	override fun setup() {
		super.setup()
		MockKAnnotations.init(this)

		setupKoin(module {
			scope<ProjectDefScope> {
				scoped<ProjectDef> { projectDef }
				scoped { sceneDraftRepository }
			}
		})
	}

	private fun newSynchronizer() = ClientSceneDraftSynchronizer(
		projectDef = projectDef,
		serverProjectApi = serverProjectApi,
		projectMetadataDatasource = projectMetadataDatasource,
		strRes = strRes,
	)

	@Test
	fun `getEntityType is SceneDraft`() {
		assertEquals(EntityType.SceneDraft, newSynchronizer().getEntityType())
	}

	@Test
	fun `ownsEntity reflects whether the draft exists`() = runTest {
		every { sceneDraftRepository.getDraftDef(5) } returns draftDef(5)
		every { sceneDraftRepository.getDraftDef(99) } returns null
		val sync = newSynchronizer()
		assertTrue(sync.ownsEntity(5))
		assertFalse(sync.ownsEntity(99))
	}

	@Test
	fun `getEntityHash is deterministic and content-sensitive and null when absent`() = runTest {
		every { sceneDraftRepository.getDraftDef(5) } returns draftDef(5)
		every { sceneDraftRepository.loadDraftContent(any()) } returns "content"
		every { sceneDraftRepository.getDraftDef(99) } returns null
		val sync = newSynchronizer()

		val hash = sync.getEntityHash(5)
		assertNotNull(hash)
		assertEquals(hash, sync.getEntityHash(5))

		// Draft content must feed the sync hash, or edits never propagate.
		every { sceneDraftRepository.loadDraftContent(any()) } returns "changed content"
		assertNotEquals(hash, sync.getEntityHash(5))

		assertNull(sync.getEntityHash(99))
	}

	@Test
	fun `createEntityForId maps the draft fields`() = runTest {
		every { sceneDraftRepository.getDraftDef(5) } returns draftDef(5, sceneId = 8, name = "first pass")
		every { sceneDraftRepository.loadDraftContent(any()) } returns "draft body"

		val entity = newSynchronizer().createEntityForId(5)

		assertEquals(5, entity.id)
		assertEquals(8, entity.sceneId)
		assertEquals("first pass", entity.name)
		assertEquals("draft body", entity.content)
		assertEquals(Instant.fromEpochSeconds(100), entity.created)
	}

	@Test
	fun `createEntityForId throws when the draft is missing`() = runTest {
		every { sceneDraftRepository.getDraftDef(99) } returns null
		assertFailsWith<IllegalStateException> { newSynchronizer().createEntityForId(99) }
	}

	@Test
	fun `storeEntity inserts the server draft`() = runTest {
		val serverEntity = ApiProjectEntity.SceneDraftEntity(
			id = 5,
			sceneId = 8,
			created = Instant.fromEpochSeconds(200),
			name = "server draft",
			content = "server body",
		)

		val result = newSynchronizer().storeEntity(serverEntity, "sync-1", {})

		assertTrue(result)
		verify { sceneDraftRepository.insertSyncDraft(serverEntity) }
	}

	@Test
	fun `reIdEntity delegates to the repository`() = runTest {
		newSynchronizer().reIdEntity(oldId = 3, newId = 9)
		coVerify { sceneDraftRepository.reIdDraft(3, 9) }
	}

	@Test
	fun `deleteEntityLocal deletes the draft and logs`() = runTest {
		val logs = mutableListOf<SyncLogMessage>()

		newSynchronizer().deleteEntityLocal(7) { logs.add(it) }

		verify { sceneDraftRepository.deleteDraft(7) }
		assertEquals(SyncLogLevel.INFO, logs.single().level)
	}
}
