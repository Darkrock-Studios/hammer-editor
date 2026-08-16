package com.darkrockstudios.apps.hammer.review

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.ApiSceneType
import com.darkrockstudios.apps.hammer.base.http.createTokenBase64
import com.darkrockstudios.apps.hammer.database.AccountDao
import com.darkrockstudios.apps.hammer.database.ProjectDao
import com.darkrockstudios.apps.hammer.database.ReviewRequestDao
import com.darkrockstudios.apps.hammer.database.ReviewSceneDao
import com.darkrockstudios.apps.hammer.database.ReviewSuggestionDao
import com.darkrockstudios.apps.hammer.dependencyinjection.PROJECTS_SYNC_MANAGER
import com.darkrockstudios.apps.hammer.dependencyinjection.PROJECT_SYNC_MANAGER
import com.darkrockstudios.apps.hammer.encryption.ContentEncryptor
import com.darkrockstudios.apps.hammer.encryption.ContentEncryptorRegistry
import com.darkrockstudios.apps.hammer.project.EntityDefinition
import com.darkrockstudios.apps.hammer.project.ProjectDefinition
import com.darkrockstudios.apps.hammer.project.ProjectEntityDatasource
import com.darkrockstudios.apps.hammer.project.ProjectSyncData
import com.darkrockstudios.apps.hammer.project.ProjectSyncKey
import com.darkrockstudios.apps.hammer.project.ProjectSynchronizationSession
import com.darkrockstudios.apps.hammer.project.synchronizers.ServerSceneDraftSynchronizer
import com.darkrockstudios.apps.hammer.project.synchronizers.ServerSceneSynchronizer
import com.darkrockstudios.apps.hammer.projects.ProjectsSynchronizationSession
import com.darkrockstudios.apps.hammer.syncsessionmanager.SyncSessionManager
import com.darkrockstudios.apps.hammer.utilities.SResult
import com.darkrockstudios.apps.hammer.utilities.ServerResult
import com.darkrockstudios.apps.hammer.utilities.TokenHasher
import com.darkrockstudios.apps.hammer.utilities.isFailure
import com.darkrockstudios.apps.hammer.utilities.isSuccess
import com.darkrockstudios.apps.hammer.utils.BaseTest
import com.darkrockstudios.apps.hammer.utils.TestClock
import com.darkrockstudios.apps.hammer.database.ReviewRequest as ReviewRequestRow
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.qualifier.named
import org.koin.dsl.module
import java.security.SecureRandom
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class ReviewRepositoryTest : BaseTest() {

	private val userId = 1L
	private val numericProjectId = 7L
	private val projectId = ProjectId("test-uuid")
	private val projectDef = ProjectDefinition("Test Project", projectId)
	private val syncKey = ProjectSyncKey(userId, projectDef)
	private val cipherSecret = "cipher-secret"
	private val testCipher = "AES/GCM/NoPadding"

	private lateinit var accountDao: AccountDao
	private lateinit var projectDao: ProjectDao
	private lateinit var reviewRequestDao: ReviewRequestDao
	private lateinit var reviewSceneDao: ReviewSceneDao
	private lateinit var reviewSuggestionDao: ReviewSuggestionDao
	private lateinit var projectEntityDatasource: ProjectEntityDatasource
	private lateinit var sceneDraftSynchronizer: ServerSceneDraftSynchronizer
	private lateinit var sceneSynchronizer: ServerSceneSynchronizer
	private lateinit var contentEncryptor: ContentEncryptor
	private lateinit var encryptorRegistry: ContentEncryptorRegistry
	private lateinit var tokenHasher: TokenHasher
	private lateinit var clock: TestClock

	private lateinit var projectsSessions: SyncSessionManager<Long, ProjectsSynchronizationSession>
	private lateinit var projectSessions: SyncSessionManager<ProjectSyncKey, ProjectSynchronizationSession>

	@BeforeEach
	override fun setup() {
		super.setup()

		accountDao = mockk()
		projectDao = mockk()
		reviewRequestDao = mockk()
		reviewSceneDao = mockk()
		reviewSuggestionDao = mockk()
		projectEntityDatasource = mockk()
		sceneDraftSynchronizer = mockk()
		sceneSynchronizer = mockk()
		contentEncryptor = mockk()
		tokenHasher = mockk()
		clock = TestClock(Clock.System)

		val secureRandom = SecureRandom()
		projectsSessions = SyncSessionManager(clock, secureRandom)
		projectSessions = SyncSessionManager(clock, secureRandom)

		setupKoin(
			module {
				single<SyncSessionManager<Long, ProjectsSynchronizationSession>>(
					named(PROJECTS_SYNC_MANAGER)
				) { projectsSessions }
				single<SyncSessionManager<ProjectSyncKey, ProjectSynchronizationSession>>(
					named(PROJECT_SYNC_MANAGER)
				) { projectSessions }
			}
		)

		coEvery { contentEncryptor.encrypt(any(), any()) } answers { "enc:" + firstArg<String>() }
		coEvery { contentEncryptor.decrypt(any(), any()) } answers {
			firstArg<String>().removePrefix("enc:")
		}
		every { contentEncryptor.cipherName() } returns testCipher
		encryptorRegistry = ContentEncryptorRegistry(listOf(contentEncryptor))
		coEvery { tokenHasher.hashToken(any()) } answers { "hash:" + firstArg<String>() }
	}

	private fun createRepository() = ReviewRepository(
		accountDao = accountDao,
		projectDao = projectDao,
		reviewRequestDao = reviewRequestDao,
		reviewSceneDao = reviewSceneDao,
		reviewSuggestionDao = reviewSuggestionDao,
		projectEntityDatasource = projectEntityDatasource,
		sceneDraftSynchronizer = sceneDraftSynchronizer,
		sceneSynchronizer = sceneSynchronizer,
		contentEncryptor = contentEncryptor,
		encryptorRegistry = encryptorRegistry,
		tokenHasher = tokenHasher,
		clock = clock,
		base64 = createTokenBase64(),
	)

	private fun testScene(id: Int) = ApiProjectEntity.SceneEntity(
		id = id,
		sceneType = ApiSceneType.Scene,
		order = id - 1,
		name = "Scene $id",
		path = listOf(0),
		content = "content $id",
	)

	private fun requestRow(
		status: ReviewStatus = ReviewStatus.SENT,
		expires: Instant? = null,
		token: String = "hash:token",
	) = ReviewRequestRow(
		id = 42L,
		user_id = userId,
		project_id = numericProjectId,
		token = token,
		reviewer_email = "reviewer@example.com",
		label = "My agent",
		note = null,
		status = status.toStringId(),
		created = clock.now(),
		expires = expires,
		opened_at = null,
		last_active_at = null,
		submitted_at = null,
		resolved_at = null,
	)

	private fun mockHappyPathProject(sceneIds: List<Int>) {
		coEvery { projectEntityDatasource.getProject(userId, projectId) } returns projectDef
		coEvery { projectDao.getProjectIdOrNull(userId, projectId) } returns numericProjectId
		coEvery { accountDao.getAccount(userId) } returns mockk {
			coEvery { cipher_secret } returns cipherSecret
		}
		coEvery {
			projectEntityDatasource.getEntityDefsByType(userId, projectDef, ApiProjectEntity.Type.SCENE)
		} returns sceneIds.map { EntityDefinition(it, ApiProjectEntity.Type.SCENE) }
		for (sceneId in sceneIds) {
			coEvery {
				projectEntityDatasource.loadEntity(
					userId, projectDef, sceneId,
					ApiProjectEntity.Type.SCENE,
					ApiProjectEntity.SceneEntity.serializer(),
				)
			} returns SResult.success(testScene(sceneId))
		}
	}

	@Test
	fun `create review request mints drafts above defensive max and stores hashed token`() = runTest {
		mockHappyPathProject(listOf(1, 2))

		coEvery { projectEntityDatasource.loadProjectSyncData(userId, projectDef) } returns
			ProjectSyncData(lastId = 5, deletedIds = setOf(3))
		coEvery { projectEntityDatasource.findLastId(userId, projectDef) } returns 4

		val syncDataUpdate = slot<(ProjectSyncData) -> ProjectSyncData>()
		coEvery {
			projectEntityDatasource.updateSyncData(userId, projectDef, capture(syncDataUpdate))
		} returns Unit

		val savedDrafts = mutableListOf<ApiProjectEntity.SceneDraftEntity>()
		coEvery {
			sceneDraftSynchronizer.saveEntity(userId, projectDef, capture(savedDrafts), null, true)
		} answers {
			assertTrue(projectSessions.hasActiveSyncSession(syncKey))
			SResult.success()
		}

		val storedToken = slot<String>()
		coEvery {
			reviewRequestDao.createRequest(
				userId, numericProjectId, capture(storedToken),
				any(), any(), any(), any(), any(),
			)
		} returns requestRow()

		val snapshots = mutableListOf<String>()
		coEvery {
			reviewSceneDao.createScene(42L, any(), any(), any(), any(), capture(snapshots), any())
		} returns Unit

		val result = createRepository().createReviewRequest(
			userId = userId,
			projectId = projectId,
			reviewerEmail = "reviewer@example.com",
			label = "My agent",
			note = "Please focus on pacing",
			expiresIn = 30.days,
			sceneIds = listOf(1, 2),
		)

		assertTrue(isSuccess(result))

		// Defensive max is max(lastId=5, findLastId=4, deletedIds.max=3) = 5
		assertEquals(listOf(6, 7), savedDrafts.map { it.id })
		assertEquals(listOf(1, 2), savedDrafts.map { it.sceneId })
		assertEquals(listOf("content 1", "content 2"), savedDrafts.map { it.content })
		assertEquals("Sent for review My agent", savedDrafts.first().name)

		val updatedSyncData = syncDataUpdate.captured(ProjectSyncData(lastId = 5, deletedIds = setOf(3)))
		assertEquals(7, updatedSyncData.lastId)

		assertEquals("hash:" + result.data.token, storedToken.captured)
		assertEquals(listOf("enc:content 1", "enc:content 2"), snapshots)

		assertFalse(projectSessions.hasActiveSyncSession(syncKey))
	}

	@Test
	fun `create review request fails while a client sync session is active`() = runTest {
		mockHappyPathProject(listOf(1))
		projectSessions.createNewSession(syncKey) { key, syncId ->
			ProjectSynchronizationSession(key.userId, key.projectDef, clock.now(), syncId, "client-install")
		}

		val result = createRepository().createReviewRequest(
			userId = userId,
			projectId = projectId,
			reviewerEmail = "reviewer@example.com",
			label = "My agent",
			note = null,
			expiresIn = null,
			sceneIds = listOf(1),
		)

		assertTrue(isFailure(result))
		coVerify(exactly = 0) { sceneDraftSynchronizer.saveEntity(any(), any(), any(), any(), any()) }
	}

	@Test
	fun `create review request rejects non-scene entity ids`() = runTest {
		coEvery { projectEntityDatasource.getProject(userId, projectId) } returns projectDef
		coEvery { projectDao.getProjectIdOrNull(userId, projectId) } returns numericProjectId
		// Entity 9 exists but is not a scene, so the scene defs list does not contain it.
		coEvery {
			projectEntityDatasource.getEntityDefsByType(userId, projectDef, ApiProjectEntity.Type.SCENE)
		} returns emptyList()

		val result = createRepository().createReviewRequest(
			userId = userId,
			projectId = projectId,
			reviewerEmail = "reviewer@example.com",
			label = "My agent",
			note = null,
			expiresIn = null,
			sceneIds = listOf(9),
		)

		assertTrue(isFailure(result))
		coVerify(exactly = 0) { sceneDraftSynchronizer.saveEntity(any(), any(), any(), any(), any()) }
	}

	@Test
	fun `failed draft store cleans up already minted drafts`() = runTest {
		mockHappyPathProject(listOf(1, 2))
		coEvery { projectEntityDatasource.loadProjectSyncData(userId, projectDef) } returns
			ProjectSyncData(lastId = 5, deletedIds = emptySet())
		coEvery { projectEntityDatasource.findLastId(userId, projectDef) } returns 5
		coEvery { projectEntityDatasource.updateSyncData(userId, projectDef, any()) } returns Unit

		coEvery {
			sceneDraftSynchronizer.saveEntity(userId, projectDef, match { it.id == 6 }, null, true)
		} returns SResult.success()
		coEvery {
			sceneDraftSynchronizer.saveEntity(userId, projectDef, match { it.id == 7 }, null, true)
		} returns SResult.failure("boom")
		coEvery { sceneDraftSynchronizer.deleteEntity(userId, projectDef, 6) } returns SResult.success()

		val result = createRepository().createReviewRequest(
			userId = userId,
			projectId = projectId,
			reviewerEmail = "reviewer@example.com",
			label = "My agent",
			note = null,
			expiresIn = null,
			sceneIds = listOf(1, 2),
		)

		assertTrue(isFailure(result))
		coVerify(exactly = 1) { sceneDraftSynchronizer.deleteEntity(userId, projectDef, 6) }
		assertFalse(projectSessions.hasActiveSyncSession(syncKey))
	}

	@Test
	fun `open review by token marks request opened on first use`() = runTest {
		coEvery { reviewRequestDao.getRequestByToken("hash:plain-token") } returns
			requestRow(status = ReviewStatus.SENT)
		coEvery { reviewRequestDao.markOpened(42L, ReviewStatus.OPENED, any()) } returns Unit

		val result = createRepository().openReviewByToken("plain-token")

		assertTrue(isSuccess(result))
		assertEquals(ReviewStatus.OPENED, result.data.request.status)
		assertTrue(result.data.firstOpen)
		coVerify(exactly = 1) { reviewRequestDao.markOpened(42L, ReviewStatus.OPENED, any()) }
	}

	@Test
	fun `open review by token does not re-mark an in progress request`() = runTest {
		coEvery { reviewRequestDao.getRequestByToken("hash:plain-token") } returns
			requestRow(status = ReviewStatus.IN_PROGRESS)

		val result = createRepository().openReviewByToken("plain-token")

		assertTrue(isSuccess(result))
		assertEquals(ReviewStatus.IN_PROGRESS, result.data.request.status)
		assertFalse(result.data.firstOpen)
		coVerify(exactly = 0) { reviewRequestDao.markOpened(any(), any(), any()) }
	}

	@Test
	fun `open review by token rejects expired request`() = runTest {
		coEvery { reviewRequestDao.getRequestByToken("hash:plain-token") } returns
			requestRow(status = ReviewStatus.OPENED, expires = clock.now() - 1.days)

		val result = createRepository().openReviewByToken("plain-token")

		assertTrue(isFailure(result))
	}

	@Test
	fun `open review by token rejects revoked request`() = runTest {
		coEvery { reviewRequestDao.getRequestByToken("hash:plain-token") } returns
			requestRow(status = ReviewStatus.CANCELED)

		val result = createRepository().openReviewByToken("plain-token")

		assertTrue(isFailure(result))
	}

	@Test
	fun `open review by token rejects unknown token`() = runTest {
		coEvery { reviewRequestDao.getRequestByToken(any()) } returns null

		val result = createRepository().openReviewByToken("nope")

		assertTrue(isFailure(result))
	}

	@Test
	fun `revoke cancels a pending review`() = runTest {
		coEvery { reviewRequestDao.getRequest(42L, userId) } returns requestRow(status = ReviewStatus.OPENED)
		coEvery { reviewRequestDao.updateStatus(42L, ReviewStatus.CANCELED) } returns Unit

		val result = createRepository().revokeReview(userId, 42L)

		assertTrue(isSuccess(result))
		coVerify(exactly = 1) { reviewRequestDao.updateStatus(42L, ReviewStatus.CANCELED) }
	}

	@Test
	fun `revoke rejects a submitted review`() = runTest {
		coEvery { reviewRequestDao.getRequest(42L, userId) } returns requestRow(status = ReviewStatus.SUBMITTED)

		val result = createRepository().revokeReview(userId, 42L)

		assertTrue(isFailure(result))
		coVerify(exactly = 0) { reviewRequestDao.updateStatus(any(), any()) }
	}

	@Test
	fun `for-edit draft names satisfy client draft name rules`() {
		val pattern = Regex("""[\da-zA-Z _']+""")

		val simple = ReviewRepository.forEditDraftName("My agent")
		assertEquals("Sent for review My agent", simple)

		val messy = ReviewRepository.forEditDraftName("Jane @ Lit-Agency (NYC) — #1!")
		assertTrue(pattern.matches(messy))

		val empty = ReviewRepository.forEditDraftName("@#$%")
		assertEquals("Sent for review", empty)

		val long = ReviewRepository.forEditDraftName("x".repeat(300))
		assertTrue(long.length <= 128)
		assertTrue(pattern.matches(long))
	}

	@Test
	fun `create review request validates email and scene list`() = runTest {
		val repo = createRepository()

		val noScenes = repo.createReviewRequest(
			userId, projectId, "reviewer@example.com", "label", null, null, emptyList()
		)
		assertTrue(isFailure(noScenes))

		val badEmail = repo.createReviewRequest(
			userId, projectId, "not-an-email", "label", null, null, listOf(1)
		)
		assertTrue(isFailure(badEmail))

		val dupes = repo.createReviewRequest(
			userId, projectId, "reviewer@example.com", "label", null, null, listOf(1, 1)
		)
		assertTrue(isFailure(dupes))

		val longLabel = repo.createReviewRequest(
			userId, projectId, "reviewer@example.com", "x".repeat(101), null, null, listOf(1)
		)
		assertTrue(isFailure(longLabel))

		val longNote = repo.createReviewRequest(
			userId, projectId, "reviewer@example.com", "label", "x".repeat(2001), null, listOf(1)
		)
		assertTrue(isFailure(longNote))
	}

	@Test
	fun `get review scenes decrypts snapshots`() = runTest {
		coEvery { accountDao.getAccount(userId) } returns mockk {
			coEvery { cipher_secret } returns cipherSecret
		}
		coEvery { reviewSceneDao.getScenesForRequest(42L) } returns listOf(
			com.darkrockstudios.apps.hammer.database.ReviewScene(
				id = 1L,
				review_request_id = 42L,
				scene_id = 1,
				draft_id = 6,
				scene_name = "Scene 1",
				scene_order = 0,
				snapshot_content = "enc:content 1",
				cipher = testCipher,
				reviewer_done = false,
			)
		)

		val request = requestRowDomain()
		val scenes = createRepository().getReviewScenes(request)

		assertEquals(1, scenes.size)
		assertEquals("content 1", scenes.single().snapshotContent)
	}

	/* ===== Author-side: accept/reject + commit ===== */

	private fun sceneRow(
		id: Long = 1L,
		sceneId: Int = 1,
		snapshot: String = "The quick brown fox",
	) = com.darkrockstudios.apps.hammer.database.ReviewScene(
		id = id,
		review_request_id = 42L,
		scene_id = sceneId,
		draft_id = 6,
		scene_name = "Scene $sceneId",
		scene_order = 0,
		snapshot_content = "enc:$snapshot",
		cipher = testCipher,
		reviewer_done = false,
	)

	private fun suggestionRow(
		id: Long = 1L,
		type: ReviewSuggestionType = ReviewSuggestionType.DELETE,
		start: Int = 4,
		end: Int = 10,
		replacement: String? = null,
		status: ReviewSuggestionStatus = ReviewSuggestionStatus.ACCEPTED,
	) = com.darkrockstudios.apps.hammer.database.ReviewSuggestion(
		id = id,
		review_scene_id = 1L,
		type = type.toStringId(),
		paragraph = 0,
		start_offset = start,
		end_offset = end,
		original_text = "",
		replacement_text = replacement,
		reason = null,
		status = status.toStringId(),
		created = clock.now(),
		updated = clock.now(),
	)

	private fun mockCommitProject(currentContent: String) {
		coEvery { projectDao.getProjectByRowId(numericProjectId) } returns
			com.darkrockstudios.apps.hammer.Project(
				id = numericProjectId,
				uuid = projectId.id,
				user_id = userId,
				name = projectDef.name,
				last_id = 6L,
				last_sync = clock.now(),
			)
		coEvery { projectEntityDatasource.getProject(userId, projectId) } returns projectDef
		coEvery { accountDao.getAccount(userId) } returns mockk {
			coEvery { cipher_secret } returns cipherSecret
		}
		coEvery { projectEntityDatasource.loadProjectSyncData(userId, projectDef) } returns
			ProjectSyncData(lastId = 6, deletedIds = emptySet())
		coEvery { projectEntityDatasource.findLastId(userId, projectDef) } returns 6
		coEvery { projectEntityDatasource.updateSyncData(userId, projectDef, any()) } returns Unit
		coEvery {
			projectEntityDatasource.loadEntity(
				userId, projectDef, 1,
				ApiProjectEntity.Type.SCENE,
				ApiProjectEntity.SceneEntity.serializer(),
			)
		} returns SResult.success(testScene(1).copy(content = currentContent))
		coEvery { reviewRequestDao.markResolved(42L, any()) } returns Unit
	}

	@Test
	fun `commit applies accepted edits to a clean scene and mints the reviewed draft`() = runTest {
		coEvery { reviewRequestDao.getRequest(42L, userId) } returns requestRow(status = ReviewStatus.SUBMITTED)
		mockCommitProject(currentContent = "The quick brown fox")
		coEvery { reviewSceneDao.getScenesForRequest(42L) } returns listOf(sceneRow())
		coEvery { reviewSuggestionDao.getSuggestionsForRequest(42L) } returns listOf(
			suggestionRow(type = ReviewSuggestionType.DELETE, start = 4, end = 10),
		)

		val savedDraft = slot<ApiProjectEntity.SceneDraftEntity>()
		coEvery {
			sceneDraftSynchronizer.saveEntity(userId, projectDef, capture(savedDraft), null, true)
		} answers {
			assertTrue(projectSessions.hasActiveSyncSession(syncKey))
			SResult.success()
		}
		val savedScene = slot<ApiProjectEntity.SceneEntity>()
		coEvery {
			sceneSynchronizer.saveEntity(userId, projectDef, capture(savedScene), null, true)
		} returns SResult.success()

		val result = createRepository().commitReview(userId, 42L)

		assertTrue(isSuccess(result))
		assertEquals(ReviewCommitOutcome.APPLIED, result.data.scenes.single().outcome)
		assertEquals("The brown fox", savedDraft.captured.content)
		assertEquals(7, savedDraft.captured.id)
		assertEquals("The brown fox", savedScene.captured.content)
		assertEquals(1, savedScene.captured.id)
		assertTrue(savedDraft.captured.name.startsWith("Editorial Review My agent"))
		coVerify(exactly = 1) { reviewRequestDao.markResolved(42L, any()) }
		assertFalse(projectSessions.hasActiveSyncSession(syncKey))
	}

	@Test
	fun `commit leaves a diverged scene alone but still mints the draft`() = runTest {
		coEvery { reviewRequestDao.getRequest(42L, userId) } returns requestRow(status = ReviewStatus.SUBMITTED)
		mockCommitProject(currentContent = "Edited since the review went out")
		coEvery { reviewSceneDao.getScenesForRequest(42L) } returns listOf(sceneRow())
		coEvery { reviewSuggestionDao.getSuggestionsForRequest(42L) } returns listOf(
			suggestionRow(type = ReviewSuggestionType.DELETE, start = 4, end = 10),
		)
		coEvery {
			sceneDraftSynchronizer.saveEntity(userId, projectDef, any(), null, true)
		} returns SResult.success()

		val result = createRepository().commitReview(userId, 42L)

		assertTrue(isSuccess(result))
		assertEquals(ReviewCommitOutcome.DIVERGED, result.data.scenes.single().outcome)
		coVerify(exactly = 0) { sceneSynchronizer.saveEntity(any(), any(), any(), any(), any()) }
		coVerify(exactly = 1) { reviewRequestDao.markResolved(42L, any()) }
	}

	@Test
	fun `commit with no accepted changes resolves without touching the project`() = runTest {
		coEvery { reviewRequestDao.getRequest(42L, userId) } returns requestRow(status = ReviewStatus.SUBMITTED)
		mockCommitProject(currentContent = "The quick brown fox")
		coEvery { reviewSceneDao.getScenesForRequest(42L) } returns listOf(sceneRow())
		coEvery { reviewSuggestionDao.getSuggestionsForRequest(42L) } returns listOf(
			suggestionRow(status = ReviewSuggestionStatus.REJECTED),
			suggestionRow(id = 2L, type = ReviewSuggestionType.COMMENT, status = ReviewSuggestionStatus.PENDING),
		)

		val result = createRepository().commitReview(userId, 42L)

		assertTrue(isSuccess(result))
		assertEquals(ReviewCommitOutcome.UNCHANGED, result.data.scenes.single().outcome)
		coVerify(exactly = 0) { sceneDraftSynchronizer.saveEntity(any(), any(), any(), any(), any()) }
		coVerify(exactly = 0) { sceneSynchronizer.saveEntity(any(), any(), any(), any(), any()) }
		coVerify(exactly = 1) { reviewRequestDao.markResolved(42L, any()) }
	}

	@Test
	fun `commit rejects a review that is not submitted`() = runTest {
		coEvery { reviewRequestDao.getRequest(42L, userId) } returns requestRow(status = ReviewStatus.IN_PROGRESS)

		val result = createRepository().commitReview(userId, 42L)

		assertTrue(isFailure(result))
		coVerify(exactly = 0) { reviewRequestDao.markResolved(any(), any()) }
	}

	@Test
	fun `commit rejects an already resolved review`() = runTest {
		coEvery { reviewRequestDao.getRequest(42L, userId) } returns requestRow(status = ReviewStatus.RESOLVED)

		val result = createRepository().commitReview(userId, 42L)

		assertTrue(isFailure(result))
		coVerify(exactly = 0) { reviewRequestDao.markResolved(any(), any()) }
	}

	@Test
	fun `set suggestion status accepts an edit and resolves a comment`() = runTest {
		coEvery { reviewRequestDao.getRequest(42L, userId) } returns requestRow(status = ReviewStatus.SUBMITTED)
		coEvery { reviewSceneDao.getScene(1L) } returns sceneRow()
		coEvery { reviewSuggestionDao.updateSuggestionStatus(any(), any(), any()) } returns Unit

		val repo = createRepository()

		coEvery { reviewSuggestionDao.getSuggestion(1L) } returns
			suggestionRow(type = ReviewSuggestionType.REWORD, replacement = "brown", status = ReviewSuggestionStatus.PENDING)
		val accept = repo.setSuggestionStatus(userId, 42L, 1L, ReviewSuggestionStatus.ACCEPTED)
		assertTrue(isSuccess(accept))
		coVerify { reviewSuggestionDao.updateSuggestionStatus(1L, ReviewSuggestionStatus.ACCEPTED, any()) }

		coEvery { reviewSuggestionDao.getSuggestion(2L) } returns
			suggestionRow(id = 2L, type = ReviewSuggestionType.COMMENT, status = ReviewSuggestionStatus.PENDING)
		val resolve = repo.setSuggestionStatus(userId, 42L, 2L, ReviewSuggestionStatus.RESOLVED)
		assertTrue(isSuccess(resolve))

		// A comment cannot be accepted, only resolved
		val badComment = repo.setSuggestionStatus(userId, 42L, 2L, ReviewSuggestionStatus.ACCEPTED)
		assertTrue(isFailure(badComment))

		// Undo back to pending is always allowed
		val undo = repo.setSuggestionStatus(userId, 42L, 1L, ReviewSuggestionStatus.PENDING)
		assertTrue(isSuccess(undo))
	}

	@Test
	fun `set suggestion status rejects reviews that are not submitted`() = runTest {
		val repo = createRepository()

		coEvery { reviewRequestDao.getRequest(42L, userId) } returns requestRow(status = ReviewStatus.IN_PROGRESS)
		assertTrue(isFailure(repo.setSuggestionStatus(userId, 42L, 1L, ReviewSuggestionStatus.ACCEPTED)))

		coEvery { reviewRequestDao.getRequest(42L, userId) } returns requestRow(status = ReviewStatus.RESOLVED)
		assertTrue(isFailure(repo.setSuggestionStatus(userId, 42L, 1L, ReviewSuggestionStatus.ACCEPTED)))

		coVerify(exactly = 0) { reviewSuggestionDao.updateSuggestionStatus(any(), any(), any()) }
	}

	@Test
	fun `set suggestion status rejects a suggestion from another review`() = runTest {
		coEvery { reviewRequestDao.getRequest(42L, userId) } returns requestRow(status = ReviewStatus.SUBMITTED)
		coEvery { reviewSuggestionDao.getSuggestion(1L) } returns suggestionRow()
		coEvery { reviewSceneDao.getScene(1L) } returns sceneRow().copy(review_request_id = 99L)

		val result = createRepository().setSuggestionStatus(userId, 42L, 1L, ReviewSuggestionStatus.ACCEPTED)

		assertTrue(isFailure(result))
		coVerify(exactly = 0) { reviewSuggestionDao.updateSuggestionStatus(any(), any(), any()) }
	}

	@Test
	fun `reviewed draft names satisfy client draft name rules`() {
		val pattern = Regex("""[\da-zA-Z _']+""")
		val at = Instant.fromEpochSeconds(1_765_000_000)

		val simple = ReviewRepository.reviewedDraftName("My agent", at)
		assertTrue(simple.startsWith("Editorial Review My agent"))
		assertTrue(pattern.matches(simple))

		val messy = ReviewRepository.reviewedDraftName("Jane @ Lit-Agency (NYC) — #1!", at)
		assertTrue(pattern.matches(messy))

		val empty = ReviewRepository.reviewedDraftName("@#$%", at)
		assertTrue(empty.startsWith("Editorial Review"))
		assertTrue(pattern.matches(empty))

		val long = ReviewRepository.reviewedDraftName("x".repeat(300), at)
		assertTrue(long.length <= 128)
		assertTrue(pattern.matches(long))
	}

	private fun requestRowDomain() = ReviewRequest(
		id = 42L,
		userId = userId,
		projectId = numericProjectId,
		reviewerEmail = "reviewer@example.com",
		label = "My agent",
		note = null,
		status = ReviewStatus.SUBMITTED,
		created = clock.now(),
		expires = null,
		openedAt = null,
		lastActiveAt = null,
		submittedAt = null,
		resolvedAt = null,
	)
}

private val <T> ServerResult<T>.data: T
	get() = (this as ServerResult.Success<T>).data
