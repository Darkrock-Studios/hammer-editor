package com.darkrockstudios.apps.hammer.integration.scenarios

import com.darkrockstudios.apps.hammer.base.http.storyideas.StoryIdea
import com.darkrockstudios.apps.hammer.base.http.synchronizer.IdeaHasher
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.ideasrepository.IdeasDatasource
import com.darkrockstudios.apps.hammer.common.data.ideasrepository.IdeasRepository
import com.darkrockstudios.apps.hammer.common.data.isSuccess
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.ClientAccountSynchronizer
import com.darkrockstudios.apps.hammer.common.data.sync.ideassync.IdeaConflict
import com.darkrockstudios.apps.hammer.common.data.sync.ideassync.IdeaConflictResolver
import com.darkrockstudios.apps.hammer.common.data.sync.ideassync.IdeasSyncDatasource
import com.darkrockstudios.apps.hammer.integration.RoundTripTestBase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import okio.Path
import okio.Path.Companion.toPath
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.koin.test.get
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Story ideas across two installs of one account, through the real account synchronizer and a real
 * server. Ideas are account-level (they live at the projects root, outside any project), so a
 * "second device" here is a second projects root — the client singletons read the root dynamically
 * from [GlobalSettingsStore], which is exactly what distinguishes one install from another.
 */
class IdeasTwoInstallSyncTest : RoundTripTestBase() {

	private val installBRoot: Path = "/clientB/projects".toPath()

	private val settingsStore: GlobalSettingsStore get() = get()
	private val ideasRepository: IdeasRepository get() = get()
	private val ideasDatasource: IdeasDatasource get() = get()
	private val ideasSyncDatasource: IdeasSyncDatasource get() = get()

	private suspend fun syncAccount(resolveConflict: IdeaConflictResolver = { null }): Boolean =
		get<ClientAccountSynchronizer>().syncProjects(
			onLog = {},
			onUnauthorized = {},
			onIdeaConflict = resolveConflict,
		)

	/** Waits out IdeasRepository's fire-and-forget disk load so the in-memory cache is current. */
	private suspend fun reloadIdeas() {
		val done = CompletableDeferred<Unit>()
		ideasRepository.loadIdeas { done.complete(Unit) }
		done.await()
	}

	/**
	 * Points the client at [root] as its projects directory — a different install of the same
	 * account. Server settings are stored per projects root, so they are (re)written for it.
	 */
	private suspend fun switchInstall(root: Path) {
		fileSystem.createDirectories(root)
		settingsStore.updateSettings { it.copy(projectsDirectory = root.toString()) }
		settingsStore.updateServerSettings(makeServerSettings())
		reloadIdeas()
	}

	private suspend fun createIdea(content: String, title: String? = null, tags: Set<String> = emptySet()): StoryIdea {
		val result = ideasRepository.createIdea(content = content, title = title, tags = tags)
		assertTrue(isSuccess(result), "failed to create idea: $result")
		return result.data
	}

	private fun serverIdeaHashes(): Map<String, String> =
		database().serverDatabase.storyIdeaQueries.getIdeaHashes(userId)
			.executeAsList()
			.associate { it.uuid to it.hash }

	/**
	 * Convergence oracle for ideas: the current install holds exactly the idea set the server
	 * holds, hash for hash, and its locked baselines agree with both.
	 */
	private suspend fun assertIdeasConverged() {
		val server = serverIdeaHashes()
		val local = ideasDatasource.loadIdeas().associate { it.id.id to IdeaHasher.hash(it) }
		assertEquals(server, local, "install's ideas did not converge with the server")

		val baselines = ideasSyncDatasource.load().baselines
			.map { (id, hash) -> id.id to hash }
			.toMap()
		assertEquals(server, baselines, "conflict baselines drifted from the agreed state")
	}

	/** Stability oracle: an extra account sync moves no idea bodies over the wire. */
	private suspend fun assertIdeasResyncSilent() {
		val wire = tapWire()
		assertTrue(syncAccount(), "resync should succeed")
		val ideaTransfers = wire.calls.filter {
			it.path.contains("/ideas/") && it.path.contains("/idea/") && it.status in 200..299
		}
		assertEquals(emptyList(), ideaTransfers, "resync moved an idea over the wire")
	}

	@Test
	@Timeout(value = 120)
	fun `ideas created, edited, and deleted converge across two installs`() = runBlocking {
		// Install A creates two ideas and pushes them up.
		settingsStore.updateServerSettings(makeServerSettings())
		reloadIdeas()
		val lighthouse = createIdea(
			content = "What if the light itself was the inheritance...",
			title = "The Lighthouse Keeper's Daughter",
			tags = setOf("gothic", "coastal"),
		)
		val courier = createIdea(content = "A courier who delivers letters to sleepers")
		assertTrue(syncAccount(), "install A initial sync")
		assertIdeasConverged()

		// A fresh install B pulls both down, files and all.
		switchInstall(installBRoot)
		assertTrue(ideasDatasource.loadIdeas().isEmpty(), "install B should start empty")
		assertTrue(syncAccount(), "install B first sync")
		reloadIdeas()
		assertIdeasConverged()
		val ideasOnB = ideasDatasource.loadIdeas().associateBy { it.id }
		assertEquals(setOf(lighthouse.id, courier.id), ideasOnB.keys)
		assertEquals(lighthouse.tags, assertNotNull(ideasOnB[lighthouse.id]).tags)

		// B edits one idea and deletes the other.
		val editResult = ideasRepository.updateIdea(
			assertNotNull(ideasOnB[lighthouse.id]).copy(content = "Edited on install B")
		)
		assertTrue(isSuccess(editResult))
		ideasRepository.deleteIdea(courier.id)
		assertTrue(syncAccount(), "install B pushes its changes")
		assertIdeasConverged()

		// Back on A: the edit lands, the tombstone prunes the deleted idea.
		switchInstall(projectsRoot)
		assertTrue(syncAccount(), "install A pulls B's changes")
		reloadIdeas()
		assertIdeasConverged()
		val ideasOnA = ideasDatasource.loadIdeas()
		assertEquals(listOf(lighthouse.id), ideasOnA.map { it.id })
		assertEquals("Edited on install B", ideasOnA.single().content)
		assertNull(ideasOnA.single().let { it.archived }, "sanity: nothing archived it")

		assertIdeasResyncSilent()
	}

	@Test
	@Timeout(value = 120)
	fun `divergent edits conflict and the resolution propagates`() = runBlocking {
		// Both installs hold the same synced idea.
		settingsStore.updateServerSettings(makeServerSettings())
		reloadIdeas()
		val idea = createIdea(content = "Original spark", title = "Spark")
		assertTrue(syncAccount(), "install A initial sync")
		switchInstall(installBRoot)
		assertTrue(syncAccount(), "install B adopts the ideas")
		reloadIdeas()

		// A edits and wins the race to the server.
		switchInstall(projectsRoot)
		val onA = assertNotNull(ideasRepository.getIdeaById(idea.id))
		assertTrue(isSuccess(ideasRepository.updateIdea(onA.copy(content = "Edited on A"))))
		assertTrue(syncAccount(), "install A pushes its edit")

		// B edits the same idea against a now-stale baseline; picking local resolves the conflict.
		switchInstall(installBRoot)
		val onB = assertNotNull(ideasRepository.getIdeaById(idea.id))
		assertTrue(isSuccess(ideasRepository.updateIdea(onB.copy(content = "Edited on B"))))
		var seenConflict: IdeaConflict? = null
		assertTrue(
			syncAccount { conflict ->
				seenConflict = conflict
				conflict.local
			},
			"install B's conflicted sync",
		)
		val conflict = assertNotNull(seenConflict, "the divergent edit must raise a conflict")
		assertEquals("Edited on A", conflict.server.content)
		assertEquals("Edited on B", conflict.local.content)
		assertIdeasConverged()

		// A pulls the resolution.
		switchInstall(projectsRoot)
		assertTrue(syncAccount(), "install A pulls the resolution")
		reloadIdeas()
		assertIdeasConverged()
		assertEquals("Edited on B", assertNotNull(ideasRepository.getIdeaById(idea.id)).content)

		assertIdeasResyncSilent()
	}
}
