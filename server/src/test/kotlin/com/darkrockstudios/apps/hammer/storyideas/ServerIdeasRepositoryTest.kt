package com.darkrockstudios.apps.hammer.storyideas

import com.darkrockstudios.apps.hammer.Account
import com.darkrockstudios.apps.hammer.base.IdeaId
import com.darkrockstudios.apps.hammer.base.http.createJsonSerializer
import com.darkrockstudios.apps.hammer.base.http.storyideas.IdeaHashItem
import com.darkrockstudios.apps.hammer.base.http.storyideas.StoryIdea
import com.darkrockstudios.apps.hammer.base.http.synchronizer.IdeasStateHasher
import com.darkrockstudios.apps.hammer.database.AccountDao
import com.darkrockstudios.apps.hammer.database.DeletedIdeaDao
import com.darkrockstudios.apps.hammer.database.IdeaHashRow
import com.darkrockstudios.apps.hammer.database.StoryIdeaDao
import com.darkrockstudios.apps.hammer.database.StoryIdeaRow
import com.darkrockstudios.apps.hammer.dependencyinjection.PROJECTS_SYNC_MANAGER
import com.darkrockstudios.apps.hammer.encryption.ContentEncryptorRegistry
import com.darkrockstudios.apps.hammer.encryption.PlaintextContentEncryptor
import com.darkrockstudios.apps.hammer.project.InvalidSyncIdException
import com.darkrockstudios.apps.hammer.projects.ProjectsSynchronizationSession
import com.darkrockstudios.apps.hammer.syncsessionmanager.SyncSessionManager
import com.darkrockstudios.apps.hammer.utilities.isFailure
import com.darkrockstudios.apps.hammer.utilities.isSuccess
import com.darkrockstudios.apps.hammer.utils.BaseTest
import io.ktor.util.logging.KtorSimpleLogger
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.qualifier.named
import org.koin.dsl.module
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

class ServerIdeasRepositoryTest : BaseTest() {

	private val userId = 1L
	private val syncId = "sync-id"
	private val ideaId = IdeaId("0198c9a1-7b2e-7c43-9f6a-2d8e41b0a55c")

	private lateinit var sessionManager: SyncSessionManager<Long, ProjectsSynchronizationSession>
	private lateinit var storyIdeaDao: StoryIdeaDao
	private lateinit var deletedIdeaDao: DeletedIdeaDao
	private lateinit var accountDao: AccountDao
	private lateinit var json: Json

	private fun idea(id: IdeaId = ideaId, content: String = "What if...") = StoryIdea(
		id = id,
		created = Instant.parse("2026-07-04T12:00:00Z"),
		updated = Instant.parse("2026-07-04T12:30:00Z"),
		content = content,
		tags = setOf("gothic"),
	)

	@BeforeEach
	override fun setup() {
		super.setup()

		sessionManager = mockk()
		every { sessionManager.validateSyncId(userId, syncId, any()) } returns true

		storyIdeaDao = mockk()
		deletedIdeaDao = mockk()
		coEvery { deletedIdeaDao.isIdeaDeleted(any(), any()) } returns false

		accountDao = mockk()
		val account = mockk<Account>()
		every { account.cipher_secret } returns "secret"
		coEvery { accountDao.getAccount(userId) } returns account

		json = createJsonSerializer()

		setupKoin(module {
			single<SyncSessionManager<Long, ProjectsSynchronizationSession>>(
				named(PROJECTS_SYNC_MANAGER)
			) { sessionManager }
		})
	}

	private fun createRepository(maxContentLength: Int = StoryIdeaDao.MAX_IDEA_CONTENT_LENGTH): ServerIdeasRepository {
		val registry = mockk<ContentEncryptorRegistry>()
		every { registry.resolve(any()) } returns PlaintextContentEncryptor()
		return ServerIdeasRepository(
			storyIdeaDao = storyIdeaDao,
			deletedIdeaDao = deletedIdeaDao,
			accountDao = accountDao,
			encryptor = PlaintextContentEncryptor(),
			encryptorRegistry = registry,
			json = json,
			log = KtorSimpleLogger("test"),
			maxContentLength = maxContentLength,
		)
	}

	@Test
	fun `Sync state returns idea hashes and tombstones`() = runTest {
		coEvery { storyIdeaDao.getIdeaHashes(userId) } returns listOf(IdeaHashRow(ideaId, "hash-1"))
		coEvery { deletedIdeaDao.getDeletedIdeas(userId) } returns setOf(IdeaId("dead-uuid"))

		val result = createRepository().getSyncState(userId, syncId)

		assertTrue(isSuccess(result))
		val state = result.data
		assertEquals(listOf(ideaId), state.ideas.map { it.id })
		assertEquals(listOf("hash-1"), state.ideas.map { it.hash })
		assertEquals(setOf(IdeaId("dead-uuid")), state.deletedIdeas)
	}

	@Test
	fun `Ideas state hash reflects the live idea set`() = runTest {
		coEvery { storyIdeaDao.getIdeaHashes(userId) } returns listOf(IdeaHashRow(ideaId, "hash-1"))

		val stateHash = createRepository().getIdeasStateHash(userId)

		assertEquals(
			IdeasStateHasher.hash(listOf(IdeaHashItem(ideaId, "hash-1"))),
			stateHash,
		)
	}

	@Test
	fun `Sync state rejects an invalid sync id`() = runTest {
		every { sessionManager.validateSyncId(userId, "wrong", any()) } returns false

		val result = createRepository().getSyncState(userId, "wrong")

		assertTrue(isFailure(result))
		assertIs<InvalidSyncIdException>(result.exception)
	}

	@Test
	fun `Upload stores the payload with the client hash and cipher tag`() = runTest {
		coEvery { storyIdeaDao.getIdea(userId, ideaId) } returns null
		coJustRun { storyIdeaDao.upsert(any(), any(), any(), any(), any()) }

		val element = json.encodeToJsonElement(StoryIdea.serializer(), idea())
		val result = createRepository().saveIdea(
			userId = userId,
			syncId = syncId,
			ideaId = ideaId,
			idea = element,
			originalHash = null,
			clientHash = "client-hash",
		)

		assertTrue(isSuccess(result))
		val outcome = result.data
		assertIs<ServerIdeasRepository.IdeaSaveResult.Saved>(outcome)
		assertEquals("client-hash", outcome.dto.hash)
		coVerify {
			storyIdeaDao.upsert(
				userId = userId,
				ideaId = ideaId,
				content = element.toString(),
				hash = "client-hash",
				cipher = PlaintextContentEncryptor.CIPHER_NAME,
			)
		}
	}

	@Test
	fun `Upload of a tombstoned idea is rejected - deletion wins`() = runTest {
		coEvery { deletedIdeaDao.isIdeaDeleted(userId, ideaId) } returns true

		val element = json.encodeToJsonElement(StoryIdea.serializer(), idea())
		val result = createRepository().saveIdea(userId, syncId, ideaId, element, null, "h")

		assertTrue(isFailure(result))
		assertIs<IdeaDeletedException>(result.exception)
		coVerify(exactly = 0) { storyIdeaDao.upsert(any(), any(), any(), any(), any()) }
	}

	@Test
	fun `Upload with a stale baseline returns the server copy as a conflict`() = runTest {
		val serverElement = json.encodeToJsonElement(StoryIdea.serializer(), idea(content = "server copy"))
		coEvery { storyIdeaDao.getIdea(userId, ideaId) } returns StoryIdeaRow(
			content = serverElement.toString(),
			hash = "server-hash",
			cipher = PlaintextContentEncryptor.CIPHER_NAME,
		)

		val element = json.encodeToJsonElement(StoryIdea.serializer(), idea(content = "local copy"))
		val result = createRepository().saveIdea(userId, syncId, ideaId, element, "stale-hash", "h")

		assertTrue(isSuccess(result))
		val outcome = result.data
		assertIs<ServerIdeasRepository.IdeaSaveResult.Conflict>(outcome)
		assertEquals("server-hash", outcome.conflict.serverHash)
		assertEquals(serverElement, outcome.conflict.server)
		coVerify(exactly = 0) { storyIdeaDao.upsert(any(), any(), any(), any(), any()) }
	}

	@Test
	fun `Upload with a matching baseline overwrites`() = runTest {
		val serverElement = json.encodeToJsonElement(StoryIdea.serializer(), idea(content = "server copy"))
		coEvery { storyIdeaDao.getIdea(userId, ideaId) } returns StoryIdeaRow(
			content = serverElement.toString(),
			hash = "server-hash",
			cipher = PlaintextContentEncryptor.CIPHER_NAME,
		)
		coJustRun { storyIdeaDao.upsert(any(), any(), any(), any(), any()) }

		val element = json.encodeToJsonElement(StoryIdea.serializer(), idea(content = "local copy"))
		val result = createRepository().saveIdea(userId, syncId, ideaId, element, "server-hash", "new-hash")

		assertTrue(isSuccess(result))
		assertIs<ServerIdeasRepository.IdeaSaveResult.Saved>(
			result.data
		)
		coVerify { storyIdeaDao.upsert(userId, ideaId, element.toString(), "new-hash", any()) }
	}

	@Test
	fun `Upload rejects a payload that is not a StoryIdea`() = runTest {
		coEvery { storyIdeaDao.getIdea(userId, ideaId) } returns null

		val result = createRepository().saveIdea(userId, syncId, ideaId, JsonPrimitive("nope"), null, "h")

		assertTrue(isFailure(result))
		assertIs<IllegalArgumentException>(result.exception)
	}

	@Test
	fun `Upload rejects a payload whose id does not match the path`() = runTest {
		coEvery { storyIdeaDao.getIdea(userId, ideaId) } returns null

		val element = json.encodeToJsonElement(StoryIdea.serializer(), idea(id = IdeaId("other-uuid")))
		val result = createRepository().saveIdea(userId, syncId, ideaId, element, null, "h")

		assertTrue(isFailure(result))
		assertIs<IllegalArgumentException>(result.exception)
	}

	@Test
	fun `Upload over the size cap fails`() = runTest {
		coEvery { storyIdeaDao.getIdea(userId, ideaId) } returns null

		val element = json.encodeToJsonElement(StoryIdea.serializer(), idea())
		val result = createRepository(maxContentLength = 10).saveIdea(userId, syncId, ideaId, element, null, "h")

		assertTrue(isFailure(result))
		assertIs<IdeaTooLargeException>(result.exception)
	}

	@Test
	fun `Download returns the stored idea and hash`() = runTest {
		val element = json.encodeToJsonElement(StoryIdea.serializer(), idea())
		coEvery { storyIdeaDao.getIdea(userId, ideaId) } returns StoryIdeaRow(
			content = element.toString(),
			hash = "hash-1",
			cipher = PlaintextContentEncryptor.CIPHER_NAME,
		)

		val result = createRepository().loadIdea(userId, syncId, ideaId)

		assertTrue(isSuccess(result))
		val dto = result.data
		assertEquals(element, dto.idea)
		assertEquals("hash-1", dto.hash)
	}

	@Test
	fun `Download of an unknown idea is not found`() = runTest {
		coEvery { storyIdeaDao.getIdea(userId, ideaId) } returns null

		val result = createRepository().loadIdea(userId, syncId, ideaId)

		assertTrue(isFailure(result))
		assertIs<IdeaNotFound>(result.exception)
	}

	@Test
	fun `A corrupt stored row is treated as missing`() = runTest {
		coEvery { storyIdeaDao.getIdea(userId, ideaId) } returns StoryIdeaRow(
			content = "not json at all {",
			hash = "hash-1",
			cipher = PlaintextContentEncryptor.CIPHER_NAME,
		)

		val result = createRepository().loadIdea(userId, syncId, ideaId)

		assertTrue(isFailure(result))
		assertIs<IdeaNotFound>(result.exception)
	}

	@Test
	fun `Delete removes the row and records a permanent tombstone`() = runTest {
		coJustRun { storyIdeaDao.deleteIdea(userId, ideaId) }
		coJustRun { deletedIdeaDao.recordIdeaDeleted(userId, ideaId) }

		val result = createRepository().deleteIdea(userId, syncId, ideaId)

		assertTrue(isSuccess(result))
		coVerify { storyIdeaDao.deleteIdea(userId, ideaId) }
		coVerify { deletedIdeaDao.recordIdeaDeleted(userId, ideaId) }
	}

	@Test
	fun `Delete rejects an invalid sync id`() = runTest {
		every { sessionManager.validateSyncId(userId, "wrong", any()) } returns false

		val result = createRepository().deleteIdea(userId, "wrong", ideaId)

		assertTrue(isFailure(result))
		assertIs<InvalidSyncIdException>(result.exception)
	}
}
