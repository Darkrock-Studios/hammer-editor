package com.darkrockstudios.apps.hammer.e2e

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.ClientEntityState
import com.darkrockstudios.apps.hammer.base.http.EntityHash
import com.darkrockstudios.apps.hammer.e2e.util.E2eTestData.createAuthToken
import com.darkrockstudios.apps.hammer.e2e.util.TestDataSet1
import com.darkrockstudios.apps.hammer.utils.SERVER_EMPTY_NO_WHITELIST
import com.darkrockstudios.apps.hammer.utils.createTestServer
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ProjectSyncTest : ProjectSyncTestBase() {

	@Test
	fun `A full ProjectSync where all entities are already up to date`(): Unit = runBlocking {
		val database = database()
		createTestServer(SERVER_EMPTY_NO_WHITELIST, fileSystem, database)
		TestDataSet1.createFullDataset(database, encryptor())
		val userId = 1L
		val authToken = createAuthToken(userId, "test-install-id", database = database, tokenHasher = tokenHasher())
		doStartServer()

		val state = ClientEntityState(
			entities = TestDataSet1.user1Project1Entities.map { entity ->
				EntityHash(
					id = entity.id,
					hash = entity.hash()
				)
			}.toSet()
		)

		client().apply {
			// Begin Sync
			val synchronizationBegan = projectSynchronizationBegan(userId, authToken, state)

			println(synchronizationBegan)
			assertEquals(emptyList(), synchronizationBegan.idSequence)
			assertEquals(setOf(7), synchronizationBegan.deletedIds)

			endSyncRequest(userId, authToken, synchronizationBegan)
		}
	}

	@Test
	fun `A full ProjectSync where two entities need processing`(): Unit = runBlocking {
		val database = database()
		createTestServer(SERVER_EMPTY_NO_WHITELIST, fileSystem, database)
		TestDataSet1.createFullDataset(database, encryptor())
		val userId = 1L
		val authToken = createAuthToken(userId, "test-install-id", database = database, tokenHasher = tokenHasher())
		doStartServer()

		// Entity is missing from the client, the other needs to be downloaded
		val entityIdMissingFromClient = 2
		// Entity should be uploaded to the server
		val entityIdToUpdate = 3

		val state = ClientEntityState(
			entities = TestDataSet1.user1Project1Entities
				.filter { entity -> entity.id != entityIdMissingFromClient }
				.map { entity ->
					if (entity.id == entityIdToUpdate) {
						EntityHash(
							id = entity.id,
							hash = "not-up-to-date"
						)
					} else {
						EntityHash(
							id = entity.id,
							hash = entity.hash()
						)
					}
				}.toSet()
		)

		client().apply {
			// Begin Sync
			val synchronizationBegan = projectSynchronizationBegan(userId, authToken, state)

			println(synchronizationBegan)
			assertEquals(
				listOf(entityIdMissingFromClient, entityIdToUpdate),
				synchronizationBegan.idSequence
			)
			assertEquals(setOf(7), synchronizationBegan.deletedIds)

			// Now walk the sequence and process entities
			synchronizationBegan.idSequence.forEach { entityId ->
				val entity = TestDataSet1.user1Project1Entities.find { it.id == entityId }!!
				when (entityId) {
					entityIdMissingFromClient -> {
						entity as ApiProjectEntity.SceneEntity
						val downloadResponse: ApiProjectEntity.SceneEntity = downloadEntity(
							userId,
							authToken,
							synchronizationBegan.syncId,
							entity.id,
							null,
						)
						assertEquals(entity, downloadResponse)
					}

					entityIdToUpdate -> {
						entity as ApiProjectEntity.SceneEntity
						val modifiedEntity = entity.copy(
							name = "updated"
						)
						val uploadResponse = uploadEntityRequest(
							userId,
							authToken,
							synchronizationBegan.syncId,
							modifiedEntity,
							entity.hash(),
						)
						assertTrue(uploadResponse.saved)

						// The upload must actually persist, not just report saved
						val roundTripped: ApiProjectEntity.SceneEntity = downloadEntity(
							userId,
							authToken,
							synchronizationBegan.syncId,
							modifiedEntity.id,
							null,
						)
						assertEquals(modifiedEntity, roundTripped)
					}
				}
			}

			endSyncRequest(userId, authToken, synchronizationBegan)
		}
	}

	@Test
	fun `A full ProjectSync where client needs all entities`(): Unit = runBlocking {
		val database = database()
		createTestServer(SERVER_EMPTY_NO_WHITELIST, fileSystem, database)
		TestDataSet1.createFullDataset(database, encryptor())
		val userId = 1L
		val authToken = createAuthToken(userId, "test-install-id", database = database, tokenHasher = tokenHasher())
		doStartServer()

		val state = ClientEntityState(entities = emptySet())

		client().apply {
			// Begin Sync
			val synchronizationBegan = projectSynchronizationBegan(userId, authToken, state)

			println(synchronizationBegan)
			assertEquals(
				TestDataSet1.user1Project1Entities.map { it.id },
				synchronizationBegan.idSequence
			)
			assertEquals(setOf(7), synchronizationBegan.deletedIds)

			// Now walk the sequence and process entities
			synchronizationBegan.idSequence.forEach { entityId ->
				val entity = TestDataSet1.user1Project1Entities.find { it.id == entityId }!!

				when (entity) {
					is ApiProjectEntity.SceneEntity -> {
						val downloadResponse: ApiProjectEntity.SceneEntity = downloadEntity(
							userId,
							authToken,
							synchronizationBegan.syncId,
							entity.id,
							null,
						)
						assertEquals(entity, downloadResponse)
					}

					is ApiProjectEntity.NoteEntity -> {
						val downloadResponse: ApiProjectEntity.NoteEntity = downloadEntity(
							userId,
							authToken,
							synchronizationBegan.syncId,
							entity.id,
							null,
						)
						assertEquals(entity, downloadResponse)
					}

					is ApiProjectEntity.TimelineEventEntity -> {
						val downloadResponse: ApiProjectEntity.TimelineEventEntity = downloadEntity(
							userId,
							authToken,
							synchronizationBegan.syncId,
							entity.id,
							null,
						)
						assertEquals(entity, downloadResponse)
					}

					is ApiProjectEntity.EncyclopediaEntryEntity -> {
						val downloadResponse: ApiProjectEntity.EncyclopediaEntryEntity =
							downloadEntity(
								userId,
								authToken,
								synchronizationBegan.syncId,
								entity.id,
								null,
							)
						assertEquals(entity, downloadResponse)
					}

					is ApiProjectEntity.SceneDraftEntity -> {
						val downloadResponse: ApiProjectEntity.SceneDraftEntity = downloadEntity(
							userId,
							authToken,
							synchronizationBegan.syncId,
							entity.id,
							null,
						)
						assertEquals(entity, downloadResponse)
					}
				}
			}

			endSyncRequest(userId, authToken, synchronizationBegan)
		}
	}

	@Test
	fun `A full ProjectSync where an entity has a conflict`(): Unit = runBlocking {
		val database = database()
		createTestServer(SERVER_EMPTY_NO_WHITELIST, fileSystem, database)
		TestDataSet1.createFullDataset(database, encryptor())
		val userId = 1L
		val authToken = createAuthToken(userId, "test-install-id", database = database, tokenHasher = tokenHasher())
		doStartServer()

		val entityIdToUpdate = 3

		val state = ClientEntityState(
			entities = TestDataSet1.user1Project1Entities
				.map { entity ->
					if (entity.id == entityIdToUpdate) {
						EntityHash(
							id = entity.id,
							hash = "hash-is-different"
						)
					} else {
						EntityHash(
							id = entity.id,
							hash = entity.hash()
						)
					}
				}.toSet()
		)

		client().apply {
			// Begin Sync
			val synchronizationBegan = projectSynchronizationBegan(userId, authToken, state)

			println(synchronizationBegan)
			assertEquals(
				listOf(entityIdToUpdate),
				synchronizationBegan.idSequence
			)
			assertEquals(setOf(7), synchronizationBegan.deletedIds)

			val entity = TestDataSet1.user1Project1Entities.find { it.id == entityIdToUpdate }!!
			entity as ApiProjectEntity.SceneEntity

			val modifiedEntity = entity.copy(
				name = "updated"
			)
			val conflictResponse = uploadConflictedEntityRequest(
				userId,
				authToken,
				synchronizationBegan.syncId,
				modifiedEntity,
				"different-original-hash",
			)
			assertEquals(entity, conflictResponse)

			val uploadResponse = uploadEntityRequest(
				userId,
				authToken,
				synchronizationBegan.syncId,
				modifiedEntity,
				entity.hash(),
				true,
			)
			assertTrue(uploadResponse.saved)

			// The forced upload must actually persist, not just report saved
			val roundTripped: ApiProjectEntity.SceneEntity = downloadEntity(
				userId,
				authToken,
				synchronizationBegan.syncId,
				modifiedEntity.id,
				null,
			)
			assertEquals(modifiedEntity, roundTripped)

			endSyncRequest(userId, authToken, synchronizationBegan)
		}
	}

	@Test
	fun `Client tries to download a deleted Entity during sync`(): Unit = runBlocking {
		val database = database()
		createTestServer(SERVER_EMPTY_NO_WHITELIST, fileSystem, database)
		TestDataSet1.createFullDataset(database, encryptor())
		val userId = 1L
		val authToken = createAuthToken(userId, "test-install-id", database = database, tokenHasher = tokenHasher())
		doStartServer()

		val entityIdToDownload = 7

		val state = ClientEntityState(
			entities = emptySet()
		)

		client().apply {
			// Begin Sync
			val synchronizationBegan = projectSynchronizationBegan(userId, authToken, state)

			val downloadResponse = downloadEntityRequest(
				userId,
				authToken,
				synchronizationBegan.syncId,
				entityIdToDownload,
				null,
			)
			assertEquals(HttpStatusCode.NotFound, downloadResponse.status)

			endSyncRequest(userId, authToken, synchronizationBegan)
		}
	}

	@Test
	fun `Beginning sync again reclaims the same user's existing session`(): Unit = runBlocking {
		val database = database()
		createTestServer(SERVER_EMPTY_NO_WHITELIST, fileSystem, database)
		TestDataSet1.createFullDataset(database, encryptor())
		val userId = 1L
		val checkEntityId = 1
		val authToken = createAuthToken(userId, "test-install-id", database = database, tokenHasher = tokenHasher())
		doStartServer()

		val state = ClientEntityState(
			entities = emptySet()
		)

		client().apply {
			val synchronizationBegan1 = projectSynchronizationBegan(userId, authToken, state)

			// A leaked/stale session must not lock the owner out: beginning again succeeds and
			// reclaims the session with a fresh sync ID.
			val synchronizationBegan2 = projectSynchronizationBegan(userId, authToken, state)
			assertNotEquals(synchronizationBegan1.syncId, synchronizationBegan2.syncId)

			// The reclaimed (old) sync ID is no longer valid.
			val staleDownloadResponse = downloadEntityRequest(
				userId,
				authToken,
				synchronizationBegan1.syncId,
				checkEntityId,
				null,
			)
			assertNotEquals(HttpStatusCode.OK, staleDownloadResponse.status)

			// The new session works.
			val downloadResponse: ApiProjectEntity.SceneEntity = downloadEntity(
				userId,
				authToken,
				synchronizationBegan2.syncId,
				checkEntityId,
				null,
			)
			assertEquals(checkEntityId, downloadResponse.id)

			endSyncRequest(userId, authToken, synchronizationBegan2)
		}
	}

	@Test
	fun `Begin sync from a different install is rejected while a session is active`(): Unit = runBlocking {
		val database = database()
		createTestServer(SERVER_EMPTY_NO_WHITELIST, fileSystem, database)
		TestDataSet1.createFullDataset(database, encryptor())
		val userId = 1L
		val checkEntityId = 1
		val authTokenA = createAuthToken(userId, "install-a", database = database, tokenHasher = tokenHasher())
		val authTokenB = createAuthToken(userId, "install-b", database = database, tokenHasher = tokenHasher())
		doStartServer()

		val state = ClientEntityState(
			entities = emptySet()
		)

		client().apply {
			val began = projectSynchronizationBegan(userId, authTokenA, state)

			// A different install must not steal an active session.
			val otherInstallResponse = projectSynchronizationBeganRequest(userId, authTokenB, state)
			assertEquals(HttpStatusCode.BadRequest, otherInstallResponse.status)

			// The original install's session is untouched and still usable.
			val download: ApiProjectEntity.SceneEntity = downloadEntity(
				userId,
				authTokenA,
				began.syncId,
				checkEntityId,
				null,
			)
			assertEquals(checkEntityId, download.id)

			endSyncRequest(userId, authTokenA, began)
		}
	}
}