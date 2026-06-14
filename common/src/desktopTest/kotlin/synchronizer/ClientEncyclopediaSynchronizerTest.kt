package synchronizer

import ENCYCLOPEDIA_ONLY_PROJECT_NAME
import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.http.SaveEntityResponse
import com.darkrockstudios.apps.hammer.base.http.synchronizer.EntityConflictException
import com.darkrockstudios.apps.hammer.base.http.synchronizer.EntityHasher
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaDatasource
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaRepository
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaService
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.data.id.IdAllocator
import com.darkrockstudios.apps.hammer.common.data.projectmetadata.ProjectMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.projectstatistics.StatisticsRepository
import com.darkrockstudios.apps.hammer.common.data.references.ReferenceIndexDatasource
import com.darkrockstudios.apps.hammer.common.data.references.ReferenceIndexRepository
import com.darkrockstudios.apps.hammer.common.data.references.ReferenceRemapper
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncJournal
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncLogMessage
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.synchronizers.ClientEncyclopediaSynchronizer
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.common.fileio.ExternalFileIo
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import com.darkrockstudios.apps.hammer.common.server.ServerProjectApi
import com.darkrockstudios.apps.hammer.common.util.StrRes
import createProject
import getProjectDef
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.launch
import korlibs.crypto.encoding.Base64
import kotlinx.coroutines.test.runTest
import net.peanuuutz.tomlkt.Toml
import okio.fakefilesystem.FakeFileSystem
import org.jetbrains.compose.resources.StringResource
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import repositories.encyclopedia.entry1
import repositories.encyclopedia.entry2
import utils.BaseTest
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Classical-style: drives a real [EncyclopediaRepository] / [EncyclopediaService] /
 * [EncyclopediaDatasource] over a [FakeFileSystem] seeded from the "Encyclopedia Only"
 * fixture (entry 1 = PERSON, entry 2 = PLACE). Only true boundaries are mocked: the
 * network ([ServerProjectApi]) and leaf side-effect collaborators. Assertions are made
 * against observable state (files on disk, returned entities) rather than interactions.
 */
class ClientEncyclopediaSynchronizerTest : BaseTest() {

	private val projectDef = getProjectDef(ENCYCLOPEDIA_ONLY_PROJECT_NAME)

	@MockK(relaxed = true)
	private lateinit var serverProjectApi: ServerProjectApi

	@MockK(relaxed = true)
	private lateinit var projectMetadataDatasource: ProjectMetadataDatasource

	@MockK(relaxed = true)
	private lateinit var idAllocator: IdAllocator

	@MockK(relaxed = true)
	private lateinit var externalFileIo: ExternalFileIo

	@MockK
	private lateinit var syncJournal: SyncJournal

	@MockK(relaxed = true)
	private lateinit var statisticsRepository: StatisticsRepository

	private val strRes: StrRes = object : StrRes {
		override suspend fun get(str: StringResource) = "test"
		override suspend fun get(str: StringResource, vararg args: Any) = "test"
	}

	private lateinit var fileSystem: FakeFileSystem
	private lateinit var toml: Toml
	private lateinit var datasource: EncyclopediaDatasource
	private lateinit var repository: EncyclopediaRepository
	private lateinit var service: EncyclopediaService
	private lateinit var remapper: RecordingReferenceRemapper

	private class RecordingReferenceRemapper : ReferenceRemapper {
		val remaps = mutableListOf<Pair<Int, Int>>()
		override suspend fun remapEntryReferences(oldEntryId: Int, newEntryId: Int) {
			remaps.add(oldEntryId to newEntryId)
		}
	}

	@BeforeEach
	override fun setup() {
		super.setup()
		MockKAnnotations.init(this, relaxUnitFun = true)

		every { syncJournal.isServerSynchronized() } returns false
		every { projectMetadataDatasource.requireProjectId(projectDef) } returns ProjectId("server-project")

		fileSystem = FakeFileSystem()
		toml = createTomlSerializer()
		createProject(fileSystem, ENCYCLOPEDIA_ONLY_PROJECT_NAME)

		// Koin must be running before the repository is built — its init resolves a
		// project scope and injects a dispatcher. The scoped providers below are lazy,
		// so they safely close over the lateinit collaborators assigned afterward.
		setupKoin(module {
			scope<ProjectDefScope> {
				scoped { projectDef }
				scoped { repository }
				scoped { service }
				scoped { datasource }
				scoped<ReferenceRemapper> { remapper }
			}
		})

		datasource = EncyclopediaDatasource(
			projectDef = projectDef,
			toml = toml,
			fileSystem = fileSystem,
			externalFileIo = externalFileIo,
		)
		repository = EncyclopediaRepository(
			projectDef = projectDef,
			idAllocator = idAllocator,
			datasource = datasource,
			syncJournal = syncJournal,
		)
		val referenceIndexDatasource = ReferenceIndexDatasource(fileSystem, toml, projectDef)
		val referenceIndexRepository = ReferenceIndexRepository(projectDef, referenceIndexDatasource)
		service = EncyclopediaService(
			repository = repository,
			statisticsRepository = statisticsRepository,
			referenceIndexRepository = referenceIndexRepository,
		)
		remapper = RecordingReferenceRemapper()
	}

	private fun newSynchronizer() = ClientEncyclopediaSynchronizer(
		projectDef = projectDef,
		serverProjectApi = serverProjectApi,
		projectMetadataDatasource = projectMetadataDatasource,
		strRes = strRes,
	)

	@Test
	fun `ownsEntity reflects which entries exist locally`() = runTest {
		val sync = newSynchronizer()
		sync.prepareForSync()

		assertTrue(sync.ownsEntity(entry1().id))
		assertTrue(sync.ownsEntity(entry2().id))
		assertFalse(sync.ownsEntity(999))
	}

	@Test
	fun `createEntityForId mirrors the stored entry`() = runTest {
		val sync = newSynchronizer()

		val entity = sync.createEntityForId(entry1().id)

		assertEquals(entry1().id, entity.id)
		assertEquals(entry1().name, entity.name)
		assertEquals(EntryType.PERSON.text, entity.entryType)
		assertEquals(entry1().text, entity.text)
		assertEquals(entry1().tags, entity.tags)
		assertNull(entity.image)
	}

	@Test
	fun `createEntityForId round-trips the entry image as base64`() = runTest {
		val imageBytes = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
		datasource.writeEntryImage(entry1().toDef(projectDef), imageBytes, "jpg")

		val sync = newSynchronizer()
		val entity = sync.createEntityForId(entry1().id)

		val image = entity.image
		assertNotNull(image)
		assertEquals("jpg", image.fileExtension)
		assertContentEquals(imageBytes, Base64.decode(image.base64, url = true))
	}

	@Test
	fun `getEntityHash is deterministic and entry-specific`() = runTest {
		val sync = newSynchronizer()

		assertEquals(sync.getEntityHash(entry1().id), sync.getEntityHash(entry1().id))
		assertNotEquals(sync.getEntityHash(entry1().id), sync.getEntityHash(entry2().id))
	}

	@Test
	fun `storeEntity creates a brand-new entry on disk`() = runTest {
		val sync = newSynchronizer()
		val serverEntity = sync.createEntityForId(entry1().id).copy(
			id = 50,
			name = "Imported",
			text = "From the server",
		)

		val stored = sync.storeEntity(serverEntity, syncId = "sync", onLog = {})
		assertTrue(stored)

		val def = repository.findEntryDef(50)
		assertNotNull(def)
		val loaded = repository.loadEntry(50).entry
		assertEquals("Imported", loaded.name)
		assertEquals("From the server", loaded.text)
	}

	@Test
	fun `storeEntity updates an existing entry in place`() = runTest {
		val sync = newSynchronizer()
		val serverEntity = sync.createEntityForId(entry1().id).copy(
			name = entry1().name,
			text = "Server-updated text",
			tags = setOf("server-tag"),
		)

		val stored = sync.storeEntity(serverEntity, syncId = "sync", onLog = {})
		assertTrue(stored)

		val loaded = repository.loadEntry(entry1().id).entry
		assertEquals("Server-updated text", loaded.text)
		assertEquals(setOf("server-tag"), loaded.tags)
	}

	@Test
	fun `storeEntity writes a server-provided image to disk`() = runTest {
		val imageBytes = byteArrayOf(9, 8, 7, 6)
		val sync = newSynchronizer()
		val serverEntity = sync.createEntityForId(entry1().id).copy(
			image = com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity.EncyclopediaEntryEntity.Image(
				base64 = Base64.encode(imageBytes, url = true),
				fileExtension = "jpg",
			)
		)

		sync.storeEntity(serverEntity, syncId = "sync", onLog = {})

		val def = entry1().toDef(projectDef)
		assertTrue(datasource.hasEntryImage(def, "jpg"))
		assertContentEquals(imageBytes, datasource.loadEntryImage(def, "jpg"))
	}

	@Test
	fun `storeEntity drops a local image when the server has none`() = runTest {
		val def = entry1().toDef(projectDef)
		datasource.writeEntryImage(def, byteArrayOf(1, 2, 3), "jpg")
		assertTrue(datasource.hasEntryImage(def, "jpg"))

		val sync = newSynchronizer()
		val serverEntity = sync.createEntityForId(entry1().id).copy(image = null)

		sync.storeEntity(serverEntity, syncId = "sync", onLog = {})

		assertFalse(datasource.hasEntryImage(def, "jpg"))
	}

	@Test
	fun `hashEntities covers every entry except the excluded new ids`() = runTest {
		val sync = newSynchronizer()
		sync.prepareForSync()

		val all = sync.hashEntities(emptyList())
		assertEquals(setOf(entry1().id, entry2().id), all.map { it.id }.toSet())

		val excluding2 = sync.hashEntities(listOf(entry2().id))
		assertEquals(setOf(entry1().id), excluding2.map { it.id }.toSet())
		assertEquals(sync.getEntityHash(entry1().id), excluding2.single().hash)
	}

	@Test
	fun `deleteEntityLocal removes the entry and emits a log`() = runTest {
		val logs = mutableListOf<SyncLogMessage>()
		val sync = newSynchronizer()

		sync.deleteEntityLocal(entry1().id, onLog = { logs.add(it) })

		assertNull(repository.findEntryDef(entry1().id))
		assertEquals(1, logs.size)
	}

	@Test
	fun `reIdEntity moves the entry on disk and remaps references`() = runTest {
		val sync = newSynchronizer()

		val oldPath = datasource.getEntryPath(entry2().toDef(projectDef)).toOkioPath()
		assertTrue(fileSystem.exists(oldPath))

		sync.reIdEntity(oldId = entry2().id, newId = 7)

		assertFalse(fileSystem.exists(oldPath))
		val newPath = datasource.getEntryPath(entry2().copy(id = 7).toDef(projectDef)).toOkioPath()
		assertTrue(fileSystem.exists(newPath))
		assertEquals(listOf(entry2().id to 7), remapper.remaps)
	}

	@Test
	fun `uploadEntity sends the entity and reports the synced hash on success`() = runTest {
		val sync = newSynchronizer()
		coEvery {
			serverProjectApi.uploadEntity(any(), any(), any(), any(), any(), any())
		} returns Result.success(SaveEntityResponse(saved = true))

		val synced = mutableListOf<Pair<Int, String>>()
		val logs = mutableListOf<SyncLogMessage>()

		val result = sync.uploadEntity(
			id = entry1().id,
			syncId = "sync",
			originalHash = "old-hash",
			onConflict = { error("no conflict expected") },
			onLog = { logs.add(it) },
			onSynced = { id, hash -> synced.add(id to hash) },
		)

		assertTrue(result)
		assertEquals(listOf(entry1().id to sync.getEntityHash(entry1().id)), synced)
		coVerify {
			serverProjectApi.uploadEntity(
				projectDef.name,
				ProjectId("server-project"),
				match { it.id == entry1().id },
				"old-hash",
				"sync",
				false,
			)
		}
	}

	@Test
	fun `uploadEntity passes the force flag through to the server`() = runTest {
		val sync = newSynchronizer()
		coEvery {
			serverProjectApi.uploadEntity(any(), any(), any(), any(), any(), any())
		} returns Result.success(SaveEntityResponse(saved = true))

		sync.uploadEntity(
			id = entry1().id,
			syncId = "sync",
			originalHash = null,
			onConflict = { error("no conflict expected") },
			onLog = {},
			force = true,
		)

		coVerify { serverProjectApi.uploadEntity(any(), any(), any(), any(), any(), true) }
	}

	@Test
	fun `uploadEntity returns false and does not report synced on a non-conflict failure`() = runTest {
		val sync = newSynchronizer()
		coEvery {
			serverProjectApi.uploadEntity(any(), any(), any(), any(), any(), any())
		} returns Result.failure(RuntimeException("server exploded"))

		val synced = mutableListOf<Pair<Int, String>>()

		val result = sync.uploadEntity(
			id = entry1().id,
			syncId = "sync",
			originalHash = null,
			onConflict = { error("no conflict expected") },
			onLog = {},
			onSynced = { id, hash -> synced.add(id to hash) },
		)

		assertFalse(result)
		assertTrue(synced.isEmpty())
	}

	@Test
	fun `uploadEntity resolves a conflict by re-uploading and storing the resolved entity`() = runTest {
		val sync = newSynchronizer()
		val serverVersion = sync.createEntityForId(entry1().id).copy(text = "server's version")
		val resolved = sync.createEntityForId(entry1().id).copy(text = "merged resolution")

		coEvery {
			serverProjectApi.uploadEntity(any(), any(), any(), "old-hash", any(), false)
		} returns Result.failure(EntityConflictException.EncyclopediaEntryConflictException(serverVersion))
		coEvery {
			serverProjectApi.uploadEntity(any(), any(), any(), null, any(), true)
		} returns Result.success(SaveEntityResponse(saved = true))

		val conflicted = mutableListOf<Int>()
		val synced = mutableListOf<Pair<Int, String>>()

		launch { sync.conflictResolution.send(resolved) }
		val result = sync.uploadEntity(
			id = entry1().id,
			syncId = "sync",
			originalHash = "old-hash",
			onConflict = { conflicted.add(it.id) },
			onLog = {},
			onSynced = { id, hash -> synced.add(id to hash) },
		)

		assertTrue(result)
		assertEquals(listOf(entry1().id), conflicted)
		assertEquals(listOf(entry1().id to resolved.hash()), synced)
		assertEquals("merged resolution", repository.loadEntry(entry1().id).entry.text)
	}

	@Test
	fun `uploadEntity returns false when conflict resolution upload fails`() = runTest {
		val sync = newSynchronizer()
		val serverVersion = sync.createEntityForId(entry1().id).copy(text = "server's version")
		val resolved = sync.createEntityForId(entry1().id).copy(text = "merged resolution")

		coEvery {
			serverProjectApi.uploadEntity(any(), any(), any(), "old-hash", any(), false)
		} returns Result.failure(EntityConflictException.EncyclopediaEntryConflictException(serverVersion))
		coEvery {
			serverProjectApi.uploadEntity(any(), any(), any(), null, any(), true)
		} returns Result.failure(RuntimeException("resolution rejected"))

		launch { sync.conflictResolution.send(resolved) }
		val result = sync.uploadEntity(
			id = entry1().id,
			syncId = "sync",
			originalHash = "old-hash",
			onConflict = {},
			onLog = {},
		)

		assertFalse(result)
	}
}
