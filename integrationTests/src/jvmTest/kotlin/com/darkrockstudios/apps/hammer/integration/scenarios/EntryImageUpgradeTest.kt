package com.darkrockstudios.apps.hammer.integration.scenarios

import com.appmattus.crypto.Algorithm
import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.createJsonSerializer
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaDatasource
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaRepository
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.ImagesRepository
import com.darkrockstudios.apps.hammer.common.data.migrator.DataMigrator
import com.darkrockstudios.apps.hammer.common.data.migrator.PROJECT_DATA_VERSION
import com.darkrockstudios.apps.hammer.common.data.projectmetadata.ProjectMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncJournal
import com.darkrockstudios.apps.hammer.e2e.util.getSerializerForType
import com.darkrockstudios.apps.hammer.encryption.ContentEncryptor
import com.darkrockstudios.apps.hammer.integration.RoundTripTestBase
import com.darkrockstudios.apps.hammer.integration.TestImages
import korlibs.crypto.encoding.Base64
import korlibs.crypto.encoding.base64Url
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import okio.FileSystem
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.koin.test.get

/**
 * A project last synced by a client from before images were their own entity: the entry's image
 * is a file next to it, the server's copy of the entry carries the image inline, and both sides
 * hold the hash that included the image bytes. After the data migration promotes the image, the
 * first sync must upload the image and the entry without a conflict, and a second device must
 * end up with both.
 */
class EntryImageUpgradeTest : RoundTripTestBase() {

	private val json = createJsonSerializer()

	@Test
	@Timeout(value = 120)
	fun `a migrated entry image syncs without conflict and reaches a second device`() = runBlocking {
		val project = "entry image upgrade"
		val client = newClient(project)
		val encyclopedia: EncyclopediaRepository = client.scope.get()
		encyclopedia.ensureEntriesLoaded()
		val entry = assertNotNull(
			encyclopedia.createEntry("Alice", EntryType.PERSON, "The keeper.", setOf("cast")).instance
		).entry
		assertTrue(client.syncNoConflict(), "initial sync should succeed")

		// Rewind both sides to how a pre-upgrade client and server left them.
		val imageBytes = TestImages.png(7)
		val serverEntity = ApiProjectEntity.EncyclopediaEntryEntity(
			id = entry.id,
			name = entry.name,
			entryType = entry.type.text,
			text = entry.text,
			tags = entry.tags,
		)
		assertEquals(serverEntity.hash(), legacyEntryHash(serverEntity, image = null), "legacy hasher replica drifted")
		val legacyHash = legacyEntryHash(serverEntity, image = imageBytes to "png")
		val serverProjectId = assertNotNull(serverNumericProjectIdFor(project))
		replaceServerEntryWithLegacyRow(serverProjectId, serverEntity, imageBytes, "png", legacyHash)

		val journal: SyncJournal = client.scope.get()
		val synced = journal.loadSyncData()
		journal.saveSyncData(synced.copy(syncedHashes = synced.syncedHashes + (entry.id to legacyHash)))

		val typeDir = client.projectPath / EncyclopediaDatasource.ENCYCLOPEDIA_DIRECTORY / EntryType.PERSON.text
		get<FileSystem>().write(typeDir / "person~${entry.id}~image.png") { write(imageBytes) }
		get<ProjectMetadataDatasource>().updateMetadata(client.projectDef) { metadata ->
			metadata.copy(info = metadata.info.copy(dataVersion = 4))
		}

		client.close()
		get<DataMigrator>().migrateProject(client.projectDef)
		assertEquals(
			PROJECT_DATA_VERSION,
			get<ProjectMetadataDatasource>().loadMetadata(client.projectDef).info.dataVersion,
		)
		val upgraded = reopen(client)

		val upgradedEncyclopedia: EncyclopediaRepository = upgraded.scope.get()
		val imageId = upgradedEncyclopedia.loadEntry(entry.id).entry.images.single()
		val upgradedImages: ImagesRepository = upgraded.scope.get()
		assertEquals("Alice", assertNotNull(upgradedImages.findImage(imageId)).name)

		assertTrue(upgraded.syncNoConflict(), "the first sync after the upgrade should not conflict")
		// The image may have been re-IDed past the server's ids; the entry follows it.
		val syncedImageId = upgradedEncyclopedia.loadEntry(entry.id).entry.images.single()
		assertNotNull(serverEntityHash(project, syncedImageId), "the promoted image should be on the server")
		assertNotEquals(legacyHash, serverEntityHash(project, entry.id), "the entry should have been re-uploaded")

		val second = secondDeviceFor(upgraded, "entry image upgrade b")
		assertTrue(second.syncNoConflict(), "second device sync should succeed")
		val secondEncyclopedia: EncyclopediaRepository = second.scope.get()
		assertEquals(listOf(syncedImageId), secondEncyclopedia.loadEntry(entry.id).entry.images)
		val secondImages: ImagesRepository = second.scope.get()
		val secondImage = assertNotNull(secondImages.findImage(syncedImageId))
		assertContentEquals(imageBytes, secondImages.loadImageBytes(secondImage))

		assertConverged(project, upgraded, second)
		assertResyncSilent(upgraded)
		assertResyncSilent(second)
	}

	/** Stores [entity] as an old server would have: the image inline in the JSON, hashed with it. */
	private fun replaceServerEntryWithLegacyRow(
		serverProjectId: Long,
		entity: ApiProjectEntity.EncyclopediaEntryEntity,
		imageBytes: ByteArray,
		imageExtension: String,
		hash: String,
	) {
		val current = json.encodeToJsonElement(getSerializerForType(entity.type), entity).jsonObject
		val legacy = JsonObject(
			current + ("image" to buildJsonObject {
				put("base64", JsonPrimitive(Base64.encode(imageBytes, url = true)))
				put("fileExtension", JsonPrimitive(imageExtension))
			})
		)

		val db = database().serverDatabase
		val cipherSecret = db.accountQueries.getAccount(userId).executeAsOne().cipher_secret
		val encryptor = encryptor() as ContentEncryptor
		val encrypted = runBlocking { encryptor.encrypt(legacy.toString(), cipherSecret) }
		db.storyEntityQueries.deleteEntity(userId = userId, projectId = serverProjectId, id = entity.id.toLong())
		db.storyEntityQueries.insertNew(
			userId = userId,
			projectId = serverProjectId,
			id = entity.id.toLong(),
			type = entity.type.toStringId(),
			content = encrypted,
			cipher = encryptor.cipherName(),
			hash = hash,
		)
	}

	/** The entry hash from before entries had an images list, when the image bytes were hashed in. */
	private fun legacyEntryHash(
		entity: ApiProjectEntity.EncyclopediaEntryEntity,
		image: Pair<ByteArray, String>?,
	): String {
		val digest = Algorithm.MurmurHash3_X64_128().createDigest()
		fun int(value: Int) = digest.update(
			byteArrayOf(value.toByte(), (value shr 8).toByte(), (value shr 16).toByte(), (value shr 24).toByte())
		)
		fun string(value: String) = value.forEach { int(it.code) }

		int(entity.id)
		string(entity.name)
		string(entity.entryType)
		string(entity.text)
		entity.tags.sorted().forEach(::string)
		entity.aliases.forEach(::string)
		if (image != null) {
			digest.update(image.first)
			string(image.second)
		}
		if (entity.excludeFromDictionary) int(1)
		return digest.digest().base64Url
	}
}
