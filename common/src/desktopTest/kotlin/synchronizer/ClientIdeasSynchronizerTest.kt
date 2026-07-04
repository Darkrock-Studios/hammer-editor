package synchronizer

import com.darkrockstudios.apps.hammer.base.IdeaId
import com.darkrockstudios.apps.hammer.base.http.HttpResponseError
import com.darkrockstudios.apps.hammer.base.http.createJsonSerializer
import com.darkrockstudios.apps.hammer.base.http.storyideas.IdeaConflictDto
import com.darkrockstudios.apps.hammer.base.http.storyideas.IdeaHashItem
import com.darkrockstudios.apps.hammer.base.http.storyideas.IdeasSyncStateResponse
import com.darkrockstudios.apps.hammer.base.http.storyideas.SavedIdeaDto
import com.darkrockstudios.apps.hammer.base.http.storyideas.StoryIdea
import com.darkrockstudios.apps.hammer.base.http.synchronizer.IdeaConflictException
import com.darkrockstudios.apps.hammer.base.http.synchronizer.IdeaHasher
import com.darkrockstudios.apps.hammer.base.http.synchronizer.IdeasStateHasher
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettings
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.ideasrepository.IdeaError
import com.darkrockstudios.apps.hammer.common.data.ideasrepository.IdeasDatasource
import com.darkrockstudios.apps.hammer.common.data.ideasrepository.IdeasRepository
import com.darkrockstudios.apps.hammer.common.data.ideasrepository.StoryIdeaCodec
import com.darkrockstudios.apps.hammer.common.data.sync.ideassync.ClientIdeasSynchronizer
import com.darkrockstudios.apps.hammer.common.data.sync.ideassync.IdeasSyncDatasource
import com.darkrockstudios.apps.hammer.common.data.sync.ideassync.IdeasSynchronizationData
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import com.darkrockstudios.apps.hammer.common.server.HttpFailureException
import com.darkrockstudios.apps.hammer.common.server.ServerIdeasApi
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import utils.BaseTest
import utils.TestStrRes
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class ClientIdeasSynchronizerTest : BaseTest() {

	private val syncId = "sync-1"

	private lateinit var ffs: FakeFileSystem
	private lateinit var ideasDatasource: IdeasDatasource
	private lateinit var syncDatasource: IdeasSyncDatasource
	private lateinit var ideasRepository: IdeasRepository
	private lateinit var api: ServerIdeasApi
	private lateinit var synchronizer: ClientIdeasSynchronizer

	// Ids must be UUID-shaped or IdeasDatasource.loadIdeas skips the files.
	private fun uuid(n: Int): String = "00000000-0000-0000-0000-" + n.toString().padStart(12, '0')

	private fun idea(id: String = uuid(1), content: String = "What if...") = StoryIdea(
		id = IdeaId(id),
		created = Instant.parse("2026-07-04T12:00:00Z"),
		updated = Instant.parse("2026-07-04T12:30:00Z"),
		content = content,
	)

	private fun serverState(
		ideas: List<StoryIdea> = emptyList(),
		deleted: Set<IdeaId> = emptySet(),
	) = IdeasSyncStateResponse(
		ideas = ideas.map { IdeaHashItem(it.id, IdeaHasher.hash(it)) },
		deletedIdeas = deleted,
	)

	@BeforeEach
	override fun setup() {
		super.setup()
		ffs = FakeFileSystem()
		val globalSettingsStore = mockk<GlobalSettingsStore>()
		every { globalSettingsStore.globalSettings } returns GlobalSettings(projectsDirectory = "/projects")
		setupKoin()

		ideasDatasource = IdeasDatasource(ffs, StoryIdeaCodec(createTomlSerializer()), globalSettingsStore)
		syncDatasource = IdeasSyncDatasource(ffs, createJsonSerializer(), ideasDatasource)
		ideasRepository = mockk(relaxed = true)
		// Mirror the real content validation so the conflict-resolution guard behaves correctly.
		every { ideasRepository.validateIdea(any(), any()) } answers {
			if (firstArg<String>().isBlank()) IdeaError.EMPTY else IdeaError.NONE
		}
		api = mockk()
		coEvery { api.deleteIdea(any(), any()) } returns Result.success("Success")

		synchronizer = ClientIdeasSynchronizer(
			ideasDatasource = ideasDatasource,
			ideasSyncDatasource = syncDatasource,
			ideasRepository = ideasRepository,
			serverIdeasApi = api,
			strRes = TestStrRes(),
		)
	}

	private suspend fun sync(): Boolean =
		synchronizer.syncIdeas(syncId, onLog = {}, resolveConflict = { null })

	@Test
	fun `First sync uploads local ideas baseline-less and locks baselines`() = runTest {
		val local = idea()
		ideasDatasource.createIdea(local)
		coEvery { api.getSyncState(syncId) } returns Result.success(serverState())
		coEvery { api.uploadIdea(local, null, syncId) } returns
			Result.success(SavedIdeaDto(local, IdeaHasher.hash(local)))

		val success = sync()

		assertTrue(success)
		coVerify { api.uploadIdea(local, null, syncId) }
		assertTrue(syncDatasource.hasSynced())
		assertEquals(IdeaHasher.hash(local), syncDatasource.load().baselines[local.id])
	}

	@Test
	fun `Server tombstones prune local copies and their bookkeeping`() = runTest {
		val doomed = idea(uuid(2))
		ideasDatasource.createIdea(doomed)
		syncDatasource.save(
			IdeasSynchronizationData(baselines = mapOf(doomed.id to IdeaHasher.hash(doomed)))
		)
		coEvery { api.getSyncState(syncId) } returns
			Result.success(serverState(deleted = setOf(doomed.id)))

		val success = sync()

		assertTrue(success)
		assertFalse(ffs.exists(ideasDatasource.getIdeaPath(doomed.id).toOkioPath()))
		assertNull(syncDatasource.load().baselines[doomed.id])
	}

	@Test
	fun `Pending deletes are pushed and erased from the outbox`() = runTest {
		val deadId = IdeaId(uuid(3))
		syncDatasource.save(IdeasSynchronizationData(pendingDeletes = setOf(deadId)))
		coEvery { api.getSyncState(syncId) } returns Result.success(serverState())

		val success = sync()

		assertTrue(success)
		coVerify { api.deleteIdea(deadId, syncId) }
		assertTrue(syncDatasource.load().pendingDeletes.isEmpty())
	}

	@Test
	fun `A successful delete is not re-downloaded even though the pre-delete server list still holds it`() = runTest {
		val deadId = IdeaId(uuid(3))
		// The state snapshot is fetched before the delete lands, so the server still lists it.
		val serverCopy = idea(uuid(3), content = "still listed on server")
		syncDatasource.save(IdeasSynchronizationData(pendingDeletes = setOf(deadId)))
		coEvery { api.getSyncState(syncId) } returns Result.success(serverState(ideas = listOf(serverCopy)))

		val success = sync()

		assertTrue(success, "propagating a delete must not report the ideas phase as failed")
		coVerify { api.deleteIdea(deadId, syncId) }
		// It must NOT be treated as a missing server idea and re-downloaded (that would 404).
		coVerify(exactly = 0) { api.downloadIdea(any(), any()) }
		assertTrue(syncDatasource.load().pendingDeletes.isEmpty())
	}

	@Test
	fun `Failed pending delete stays in the outbox and is not re-downloaded`() = runTest {
		val deadId = IdeaId(uuid(3))
		val serverCopy = idea(uuid(3), content = "still on server")
		syncDatasource.save(IdeasSynchronizationData(pendingDeletes = setOf(deadId)))
		coEvery { api.getSyncState(syncId) } returns Result.success(serverState(ideas = listOf(serverCopy)))
		coEvery { api.deleteIdea(deadId, syncId) } returns Result.failure(Exception("boom"))

		val success = sync()

		assertFalse(success)
		assertEquals(setOf(deadId), syncDatasource.load().pendingDeletes)
		coVerify(exactly = 0) { api.downloadIdea(deadId, any()) }
		assertFalse(ffs.exists(ideasDatasource.getIdeaPath(deadId).toOkioPath()))
	}

	@Test
	fun `Server-side change downloads over a clean local copy`() = runTest {
		val local = idea(content = "old content")
		val serverCopy = local.copy(content = "new content from elsewhere")
		ideasDatasource.createIdea(local)
		syncDatasource.save(
			IdeasSynchronizationData(baselines = mapOf(local.id to IdeaHasher.hash(local)))
		)
		coEvery { api.getSyncState(syncId) } returns Result.success(serverState(ideas = listOf(serverCopy)))
		coEvery { api.downloadIdea(local.id, syncId) } returns
			Result.success(SavedIdeaDto(serverCopy, IdeaHasher.hash(serverCopy)))

		val success = sync()

		assertTrue(success)
		assertEquals(listOf(serverCopy), ideasDatasource.loadIdeas())
		assertEquals(IdeaHasher.hash(serverCopy), syncDatasource.load().baselines[local.id])
	}

	@Test
	fun `Missing server ideas are downloaded`() = runTest {
		val remote = idea(uuid(4), content = "from another device")
		coEvery { api.getSyncState(syncId) } returns Result.success(serverState(ideas = listOf(remote)))
		coEvery { api.downloadIdea(remote.id, syncId) } returns
			Result.success(SavedIdeaDto(remote, IdeaHasher.hash(remote)))

		val success = sync()

		assertTrue(success)
		assertEquals(listOf(remote), ideasDatasource.loadIdeas())
		assertEquals(IdeaHasher.hash(remote), syncDatasource.load().baselines[remote.id])
	}

	@Test
	fun `Conflict resolution force-uploads the resolved idea and updates local state`() = runTest {
		val local = idea(content = "local edit")
		val serverCopy = local.copy(content = "server edit")
		val serverHash = IdeaHasher.hash(serverCopy)
		val resolved = local.copy(content = "merged")
		ideasDatasource.createIdea(local)
		syncDatasource.save(IdeasSynchronizationData(baselines = mapOf(local.id to "stale-baseline")))

		coEvery { api.getSyncState(syncId) } returns Result.success(serverState(ideas = listOf(serverCopy)))
		coEvery { api.uploadIdea(local, "stale-baseline", syncId) } returns
			Result.failure(IdeaConflictException(IdeaConflictDto(serverCopy, serverHash)))
		coEvery { api.uploadIdea(resolved, serverHash, syncId) } returns
			Result.success(SavedIdeaDto(resolved, IdeaHasher.hash(resolved)))

		val success = synchronizer.syncIdeas(syncId, onLog = {}) { conflict ->
			assertEquals(local, conflict.local)
			assertEquals(serverCopy, conflict.server)
			resolved
		}

		assertTrue(success)
		coVerify { api.uploadIdea(resolved, serverHash, syncId) }
		assertEquals(listOf(resolved), ideasDatasource.loadIdeas())
		assertEquals(IdeaHasher.hash(resolved), syncDatasource.load().baselines[local.id])
	}

	@Test
	fun `Unresolved conflict leaves the idea dirty for next time`() = runTest {
		val local = idea(content = "local edit")
		val serverCopy = local.copy(content = "server edit")
		ideasDatasource.createIdea(local)
		syncDatasource.save(IdeasSynchronizationData(baselines = mapOf(local.id to "stale-baseline")))

		coEvery { api.getSyncState(syncId) } returns Result.success(serverState(ideas = listOf(serverCopy)))
		coEvery { api.uploadIdea(local, "stale-baseline", syncId) } returns
			Result.failure(IdeaConflictException(IdeaConflictDto(serverCopy, IdeaHasher.hash(serverCopy))))

		val success = synchronizer.syncIdeas(syncId, onLog = {}) { null }

		assertTrue(success)
		assertEquals(listOf(local), ideasDatasource.loadIdeas())
		assertEquals("stale-baseline", syncDatasource.load().baselines[local.id])
	}

	@Test
	fun `An invalid conflict resolution is not uploaded or persisted`() = runTest {
		val local = idea(content = "local edit")
		val serverCopy = local.copy(content = "server edit")
		ideasDatasource.createIdea(local)
		syncDatasource.save(IdeasSynchronizationData(baselines = mapOf(local.id to "stale-baseline")))

		coEvery { api.getSyncState(syncId) } returns Result.success(serverState(ideas = listOf(serverCopy)))
		coEvery { api.uploadIdea(local, "stale-baseline", syncId) } returns
			Result.failure(IdeaConflictException(IdeaConflictDto(serverCopy, IdeaHasher.hash(serverCopy))))

		// The freely-editable local pane yields a blank (invalid) merge.
		val blank = local.copy(content = "   ")
		val success = synchronizer.syncIdeas(syncId, onLog = {}) { blank }

		assertTrue(success)
		// The invalid resolution must never reach the server or disk.
		coVerify(exactly = 0) { api.uploadIdea(blank, any(), syncId) }
		assertEquals(listOf(local), ideasDatasource.loadIdeas())
	}

	@Test
	fun `Upload of an idea tombstoned mid-flight deletes the local copy - deletion wins`() = runTest {
		val local = idea(content = "edited after another device deleted it")
		ideasDatasource.createIdea(local)
		syncDatasource.save(IdeasSynchronizationData(baselines = mapOf(local.id to "old-baseline")))

		coEvery { api.getSyncState(syncId) } returns Result.success(serverState())
		coEvery { api.uploadIdea(local, "old-baseline", syncId) } returns Result.failure(
			HttpFailureException(
				statusCode = HttpStatusCode.Gone,
				error = HttpResponseError(error = "Gone", displayMessage = "deleted"),
			)
		)

		val success = sync()

		assertTrue(success)
		assertFalse(ffs.exists(ideasDatasource.getIdeaPath(local.id).toOkioPath()))
		assertNull(syncDatasource.load().baselines[local.id])
	}

	@Test
	fun `Failed state fetch aborts the phase`() = runTest {
		coEvery { api.getSyncState(syncId) } returns Result.failure(Exception("no server"))

		val success = sync()

		assertFalse(success)
		assertFalse(syncDatasource.hasSynced())
	}

	@Test
	fun `Matching state hash skips the phase without touching the server`() = runTest {
		val local = idea()
		val localHash = IdeaHasher.hash(local)
		ideasDatasource.createIdea(local)
		syncDatasource.save(IdeasSynchronizationData(baselines = mapOf(local.id to localHash)))
		val stateHash = IdeasStateHasher.hash(listOf(IdeaHashItem(local.id, localHash)))

		val success = synchronizer.syncIdeas(
			syncId,
			onLog = {},
			resolveConflict = { null },
			serverIdeasStateHash = stateHash,
		)

		assertTrue(success)
		coVerify(exactly = 0) { api.getSyncState(any()) }
	}

	@Test
	fun `A dirty local idea defeats the state-hash skip`() = runTest {
		val original = idea()
		val edited = original.copy(content = "edited offline")
		ideasDatasource.createIdea(edited)
		syncDatasource.save(
			IdeasSynchronizationData(baselines = mapOf(original.id to IdeaHasher.hash(original)))
		)
		// The server still agrees with the old baseline...
		val staleStateHash = IdeasStateHasher.hash(
			listOf(IdeaHashItem(original.id, IdeaHasher.hash(original)))
		)
		coEvery { api.getSyncState(syncId) } returns Result.success(serverState(ideas = listOf(original)))
		coEvery { api.uploadIdea(edited, IdeaHasher.hash(original), syncId) } returns
			Result.success(SavedIdeaDto(edited, IdeaHasher.hash(edited)))

		val success = synchronizer.syncIdeas(
			syncId,
			onLog = {},
			resolveConflict = { null },
			serverIdeasStateHash = staleStateHash,
		)

		assertTrue(success)
		coVerify { api.uploadIdea(edited, IdeaHasher.hash(original), syncId) }
	}

	@Test
	fun `Pending deletes defeat the state-hash skip`() = runTest {
		val deadId = IdeaId(uuid(3))
		syncDatasource.save(IdeasSynchronizationData(pendingDeletes = setOf(deadId)))
		coEvery { api.getSyncState(syncId) } returns Result.success(serverState())

		val success = synchronizer.syncIdeas(
			syncId,
			onLog = {},
			resolveConflict = { null },
			// Both sides hold zero live ideas, so the state hashes match...
			serverIdeasStateHash = IdeasStateHasher.hash(emptyList()),
		)

		assertTrue(success)
		// ...but the outbox still has to drain.
		coVerify { api.deleteIdea(deadId, syncId) }
	}

	@Test
	fun `Ideas already in agreement get their baselines backfilled`() = runTest {
		val local = idea()
		ideasDatasource.createIdea(local)
		// No sidecar at all — e.g. it was lost; the idea exists identically on both sides.
		coEvery { api.getSyncState(syncId) } returns Result.success(serverState(ideas = listOf(local)))

		val success = sync()

		assertTrue(success)
		coVerify(exactly = 0) { api.uploadIdea(any(), any(), any()) }
		coVerify(exactly = 0) { api.downloadIdea(any(), any()) }
		assertEquals(IdeaHasher.hash(local), syncDatasource.load().baselines[local.id])
	}
}
