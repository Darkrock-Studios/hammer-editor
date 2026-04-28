package com.darkrockstudios.apps.hammer.e2e

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.ClientEntityState
import com.darkrockstudios.apps.hammer.base.http.EntityHash
import com.darkrockstudios.apps.hammer.base.http.synchronizer.EntityHasher
import com.darkrockstudios.apps.hammer.e2e.util.E2eTestData.createAuthToken
import com.darkrockstudios.apps.hammer.e2e.util.TestDataSetWithReferences
import com.darkrockstudios.apps.hammer.utilities.hashEntity
import com.darkrockstudios.apps.hammer.utils.SERVER_EMPTY_NO_WHITELIST
import com.darkrockstudios.apps.hammer.utils.createTestServer
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * End-to-end sync coverage for the reference-index fields added to scenes
 * ([ApiProjectEntity.SceneEntity.confirmedReferences],
 * [ApiProjectEntity.SceneEntity.dismissedReferences]) and the aliases field added to
 * encyclopedia entries ([ApiProjectEntity.EncyclopediaEntryEntity.aliases]).
 *
 * These tests close the loop on the bug class that bit us in Tier 2: data
 * round-tripped through JSON serialization correctly, but the persisted hash
 * disagreed with the client hash, causing perpetual sync drift. By exercising the
 * actual HTTP pipeline (begin_sync, download, upload, hash agreement), they prove
 * the new fields make it all the way through the wire and storage.
 */
class ProjectSyncReferenceFieldsTest : ProjectSyncTestBase() {

	@Test
	fun `Scene with reference fields and entry with aliases are detected as up-to-date`(): Unit =
		runBlocking {
			// Defends against client/server hash divergence on the new fields. If either
			// side silently drops a field from its hash, the server will report drift on
			// these entities and the assertion below will fail with a non-empty
			// idSequence.
			val database = database()
			createTestServer(SERVER_EMPTY_NO_WHITELIST, fileSystem, database)
			TestDataSetWithReferences.createFullDataset(database, encryptor())
			val userId = 1L
			val authToken = createAuthToken(
				userId, "test-install-id", database = database, tokenHasher = tokenHasher(),
			)
			doStartServer()

			val state = ClientEntityState(
				entities = TestDataSetWithReferences.entities.map { entity ->
					EntityHash(
						id = entity.id,
						hash = EntityHasher.hashEntity(entity),
					)
				}.toSet()
			)

			client().apply {
				val synchronizationBegan = projectSynchronizationBegan(userId, authToken, state)
				assertEquals(
					emptyList(), synchronizationBegan.idSequence,
					"Server reports drift on entities the client already has at the right hash - " +
						"client and server hashers disagree on the reference / alias fields"
				)
				endSyncRequest(userId, authToken, synchronizationBegan)
			}
		}

	@Test
	fun `Scene confirmedReferences and dismissedReferences round-trip through download`(): Unit =
		runBlocking {
			val database = database()
			createTestServer(SERVER_EMPTY_NO_WHITELIST, fileSystem, database)
			TestDataSetWithReferences.createFullDataset(database, encryptor())
			val userId = 1L
			val authToken = createAuthToken(
				userId, "test-install-id", database = database, tokenHasher = tokenHasher(),
			)
			doStartServer()

			val state = ClientEntityState(entities = emptySet())

			client().apply {
				val synchronizationBegan = projectSynchronizationBegan(userId, authToken, state)

				val downloaded: ApiProjectEntity.SceneEntity = downloadEntity(
					userId,
					authToken,
					synchronizationBegan.syncId,
					TestDataSetWithReferences.SCENE_WITH_REFS_ID,
					null,
				)

				// Deep equality - if confirmedReferences or dismissedReferences gets
				// silently stripped anywhere on the round trip (DB, JSON, HTTP), this
				// fails because they're properties of the data class.
				assertEquals(TestDataSetWithReferences.sceneWithRefs, downloaded)

				endSyncRequest(userId, authToken, synchronizationBegan)
			}
		}

	@Test
	fun `Encyclopedia entry aliases round-trip through download`(): Unit = runBlocking {
		val database = database()
		createTestServer(SERVER_EMPTY_NO_WHITELIST, fileSystem, database)
		TestDataSetWithReferences.createFullDataset(database, encryptor())
		val userId = 1L
		val authToken = createAuthToken(
			userId, "test-install-id", database = database, tokenHasher = tokenHasher(),
		)
		doStartServer()

		val state = ClientEntityState(entities = emptySet())

		client().apply {
			val synchronizationBegan = projectSynchronizationBegan(userId, authToken, state)

			val downloaded: ApiProjectEntity.EncyclopediaEntryEntity = downloadEntity(
				userId,
				authToken,
				synchronizationBegan.syncId,
				TestDataSetWithReferences.ENTRY_WITH_ALIASES_ID,
				null,
			)

			assertEquals(TestDataSetWithReferences.entryWithAliases, downloaded)

			endSyncRequest(userId, authToken, synchronizationBegan)
		}
	}

	@Test
	fun `Modified reference fields upload, persist, and re-download intact`(): Unit = runBlocking {
		// The full write path: client mutates the new fields locally, uploads with the
		// previous hash as proof of "based on" version, server accepts (no spurious
		// conflict), and a subsequent download returns the new value. Defends against
		// the write side dropping or mis-storing the fields, which would otherwise be
		// invisible until two real clients tried to share the data.
		val database = database()
		createTestServer(SERVER_EMPTY_NO_WHITELIST, fileSystem, database)
		TestDataSetWithReferences.createFullDataset(database, encryptor())
		val userId = 1L
		val authToken = createAuthToken(
			userId, "test-install-id", database = database, tokenHasher = tokenHasher(),
		)
		doStartServer()

		val originalScene = TestDataSetWithReferences.sceneWithRefs
		val originalEntry = TestDataSetWithReferences.entryWithAliases

		val state = ClientEntityState(
			entities = TestDataSetWithReferences.entities.map { entity ->
				EntityHash(
					id = entity.id,
					hash = EntityHasher.hashEntity(entity),
				)
			}.toSet()
		)

		client().apply {
			val synchronizationBegan = projectSynchronizationBegan(userId, authToken, state)

			val mutatedScene = originalScene.copy(
				confirmedReferences = setOf(7, 8),
				dismissedReferences = setOf(42),
			)
			val sceneUpload = uploadEntityRequest(
				userId,
				authToken,
				synchronizationBegan.syncId,
				mutatedScene,
				EntityHasher.hashEntity(originalScene),
			)
			assertTrue(sceneUpload.saved, "Server rejected scene upload with mutated reference fields")

			val mutatedEntry = originalEntry.copy(
				aliases = listOf("Robbie"),
			)
			val entryUpload = uploadEntityRequest(
				userId,
				authToken,
				synchronizationBegan.syncId,
				mutatedEntry,
				EntityHasher.hashEntity(originalEntry),
			)
			assertTrue(entryUpload.saved, "Server rejected encyclopedia entry upload with mutated aliases")

			val sceneAfter: ApiProjectEntity.SceneEntity = downloadEntity(
				userId,
				authToken,
				synchronizationBegan.syncId,
				originalScene.id,
				null,
			)
			assertEquals(mutatedScene, sceneAfter)

			val entryAfter: ApiProjectEntity.EncyclopediaEntryEntity = downloadEntity(
				userId,
				authToken,
				synchronizationBegan.syncId,
				originalEntry.id,
				null,
			)
			assertEquals(mutatedEntry, entryAfter)

			endSyncRequest(userId, authToken, synchronizationBegan)
		}
	}
}
