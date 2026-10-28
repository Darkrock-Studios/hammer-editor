package datamigrator

import com.darkrockstudios.apps.hammer.base.http.createJsonSerializer
import com.darkrockstudios.apps.hammer.base.http.synchronizer.EntityHasher
import com.darkrockstudios.apps.hammer.base.image.ImageFormat
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaDatasource
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContainer
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContent
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.ImagesDatasource
import com.darkrockstudios.apps.hammer.common.data.migrator.EntryImagePromoter
import com.darkrockstudios.apps.hammer.common.data.migrator.Migration4_5
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.EntityOriginalState
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.ProjectSynchronizationData
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncJournalDatasource
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import okio.FileSystem
import okio.ForwardingFileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testProjectId
import utils.BaseTest
import utils.TestImages
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class Migration4_5Test : BaseTest() {

	private lateinit var ffs: FakeFileSystem
	private lateinit var projDef: ProjectDef
	private val toml = createTomlSerializer()
	private val json = createJsonSerializer()

	@BeforeEach
	override fun setup() {
		super.setup()
		ffs = FakeFileSystem()
		val projectPath = "/projects".toPath().div("Test Project")
		ffs.createDirectories(projectPath)
		projDef = ProjectDef(id = testProjectId("Test Project"), name = "Test Project", path = projectPath.toHPath())
	}

	private fun migrate(fileSystem: FileSystem = ffs) =
		Migration4_5(EntryImagePromoter(fileSystem, toml, json)).migrate(projDef)

	private fun hashOf(entry: EntryContent) = EntityHasher.hashEncyclopediaEntry(
		id = entry.id,
		name = entry.name,
		entryType = entry.type.text,
		text = entry.text,
		tags = entry.tags,
		aliases = entry.aliases,
		excludeFromDictionary = entry.excludeFromDictionary,
		images = entry.images,
	)

	private fun typeDir(type: EntryType): Path {
		val dir = projDef.path.toOkioPath() / EncyclopediaDatasource.ENCYCLOPEDIA_DIRECTORY / type.text
		ffs.createDirectories(dir)
		return dir
	}

	private val imagesDir: Path get() = projDef.path.toOkioPath() / ImagesDatasource.IMAGES_DIRECTORY

	private val syncPath: Path get() = projDef.path.toOkioPath() / SyncJournalDatasource.SYNC_FILE_NAME

	private fun entry(id: Int, name: String, type: EntryType = EntryType.PERSON, images: List<Int> = emptyList()) =
		EntryContent(id = id, name = name, type = type, text = "About $name", tags = setOf("t"), images = images)

	private fun writeEntry(fileName: String, content: EntryContent): Path {
		val path = typeDir(content.type) / fileName
		ffs.write(path) { writeUtf8(toml.encodeToString(EntryContainer(content))) }
		return path
	}

	private fun readEntry(path: Path): EntryContent =
		toml.decodeFromString<EntryContainer>(ffs.read(path) { readUtf8() }).entry

	private fun writeFile(path: Path, bytes: ByteArray) {
		ffs.write(path) { write(bytes) }
	}

	private fun imageFiles(): List<String> = ffs.listOrNull(imagesDir).orEmpty().map { it.name }

	private fun writeSync(data: ProjectSynchronizationData) {
		ffs.write(syncPath) { writeUtf8(json.encodeToString(data)) }
	}

	private fun readSync(): ProjectSynchronizationData =
		json.decodeFromString(ffs.read(syncPath) { readUtf8() })

	private fun syncData(
		lastId: Int = 0,
		newIds: List<Int> = emptyList(),
		dirty: List<EntityOriginalState> = emptyList(),
		deletedIds: Set<Int> = emptySet(),
		syncedHashes: Map<Int, String> = emptyMap(),
	) = ProjectSynchronizationData(
		currentSyncId = null,
		lastId = lastId,
		newIds = newIds,
		lastSync = Instant.fromEpochSeconds(1_700_000_000),
		dirty = dirty,
		deletedIds = deletedIds,
		syncedHashes = syncedHashes,
		cachedProjectHash = "cached",
		hashAlgoVersion = 3,
		verifyAllEntities = false,
	)

	@Test
	fun `promotes current and legacy entry images and lists them in their entries`() {
		val alice = writeEntry("person~1~Alice.toml", entry(1, "Alice"))
		writeFile(typeDir(EntryType.PERSON) / "person~1~image.png", TestImages.png(1))
		val castle = writeEntry("place-4-Castle.toml", entry(4, "Castle", EntryType.PLACE))
		writeFile(typeDir(EntryType.PLACE) / "place-4-image.JPG", TestImages.jpeg(2))

		migrate()

		// Entry types are walked in EntryType order: person, then place.
		assertEquals(listOf("5~Alice.png", "6~Castle.jpg"), imageFiles().sorted())
		assertContentEquals(TestImages.png(1), ffs.read(imagesDir / "5~Alice.png") { readByteArray() })
		assertEquals(listOf(5), readEntry(alice).images)
		assertEquals(listOf(6), readEntry(castle).images)
		assertEquals("About Alice", readEntry(alice).text)
		assertFalse(ffs.exists(typeDir(EntryType.PERSON) / "person~1~image.png"))
		assertFalse(ffs.exists(typeDir(EntryType.PLACE) / "place-4-image.JPG"))
	}

	@Test
	fun `allocates ids above every id on disk and in the sync journal`() {
		writeEntry("person~1~Alice.toml", entry(1, "Alice"))
		writeFile(typeDir(EntryType.PERSON) / "person~1~image.png", TestImages.png())
		writeEntry("person~4~Bob.toml", entry(4, "Bob"))
		ffs.createDirectories(imagesDir)
		writeFile(imagesDir / "8~Map.png", TestImages.png())
		writeSync(syncData(lastId = 7, newIds = listOf(9), deletedIds = setOf(12)))

		migrate()

		assertEquals(listOf("13~Alice.png", "8~Map.png"), imageFiles().sorted())
	}

	@Test
	fun `adds the images and entries to the sync journal and leaves the rest of it alone`() {
		val aliceBefore = entry(1, "Alice")
		val alice = writeEntry("person~1~Alice.toml", aliceBefore)
		writeFile(typeDir(EntryType.PERSON) / "person~1~image.png", TestImages.png())
		writeEntry("person~2~Bob.toml", entry(2, "Bob"))
		writeFile(typeDir(EntryType.PERSON) / "person~2~image.webp", "RIFF0000WEBP".encodeToByteArray())
		val before = syncData(
			lastId = 3,
			newIds = listOf(3),
			dirty = listOf(EntityOriginalState(2, "bob-dirty-baseline")),
			deletedIds = setOf(1000),
			syncedHashes = mapOf(1 to "alice-synced", 2 to "bob-synced", 3 to "other-synced"),
		)
		writeSync(before)

		migrate()

		val after = readSync()
		val aliceImage = readEntry(alice).images.single()
		assertEquals(listOf(3, aliceImage, aliceImage + 1), after.newIds)
		// The clean entry's baseline becomes the hash the server gives its stored, image-less copy;
		// the dirty one keeps both of its baselines.
		val aliceBaseline = hashOf(aliceBefore)
		assertEquals(
			listOf(EntityOriginalState(2, "bob-dirty-baseline"), EntityOriginalState(1, aliceBaseline)),
			after.dirty,
		)
		assertEquals(mapOf(1 to aliceBaseline, 2 to "bob-synced", 3 to "other-synced"), after.syncedHashes)
		assertNull(after.cachedProjectHash)
		assertEquals(before.lastId, after.lastId)
		assertEquals(before.deletedIds, after.deletedIds)
		assertEquals(before.currentSyncId, after.currentSyncId)
		assertEquals(before.verifyAllEntities, after.verifyAllEntities)
		assertEquals(before.lastSync, after.lastSync)
	}

	@Test
	fun `an entry with no synced baseline is not given one`() {
		writeEntry("person~1~Alice.toml", entry(1, "Alice"))
		writeFile(typeDir(EntryType.PERSON) / "person~1~image.png", TestImages.png())
		writeSync(syncData(lastId = 1, newIds = listOf(1)))

		migrate()

		val after = readSync()
		assertEquals(emptyMap(), after.syncedHashes)
		assertEquals(listOf(EntityOriginalState(1, null)), after.dirty)
	}

	@Test
	fun `an entry name with a trailing dot names its image without it`() {
		val chapter = writeEntry("person~1~Chapter 1.toml", entry(1, "Chapter 1."))
		writeFile(typeDir(EntryType.PERSON) / "person~1~image.png", TestImages.png())
		writeEntry("person~2~Act 2.toml", entry(2, "Act 2 ."))
		writeFile(typeDir(EntryType.PERSON) / "person~2~image.png", TestImages.png())

		migrate()

		assertEquals(listOf("3~Chapter 1.png", "4~Act 2.png"), imageFiles().sortedBy { it.substringBefore('~').toInt() })
		assertEquals(listOf(3), readEntry(chapter).images)
	}

	@Test
	fun `an image a failed move left behind is promoted by the next run`() {
		val dir = typeDir(EntryType.PERSON)
		val alice = writeEntry("person~1~Alice.toml", entry(1, "Alice"))
		writeFile(dir / "person~1~image.png", TestImages.png(1))
		val bob = writeEntry("person~2~Bob.toml", entry(2, "Bob"))
		writeFile(dir / "person~2~image.png", TestImages.png(2))
		writeSync(syncData(lastId = 2))
		val lockedImage = dir / "person~1~image.png"
		val locked = object : ForwardingFileSystem(ffs) {
			override fun atomicMove(source: Path, target: Path) {
				if (source == lockedImage) throw IOException("locked by a sync tool")
				super.atomicMove(source, target)
			}
		}

		migrate(locked)

		assertTrue(ffs.exists(lockedImage))
		assertTrue(readEntry(alice).images.isEmpty())
		val bobImage = readEntry(bob).images.single()

		// A sync since then confirmed Bob with his image.
		val bobSynced = hashOf(readEntry(bob))
		writeSync(syncData(lastId = bobImage, syncedHashes = mapOf(1 to "alice-synced", 2 to bobSynced)))
		val aliceBefore = readEntry(alice)

		EntryImagePromoter(ffs, toml, json).promote(projDef)

		assertFalse(ffs.exists(lockedImage))
		val aliceImage = readEntry(alice).images.single()
		assertContentEquals(TestImages.png(1), ffs.read(imagesDir / "$aliceImage~Alice.png") { readByteArray() })
		assertEquals(listOf(bobImage), readEntry(bob).images)
		val after = readSync()
		assertEquals(listOf(aliceImage), after.newIds)
		assertEquals(listOf(EntityOriginalState(1, hashOf(aliceBefore))), after.dirty)
		assertEquals(mapOf(1 to hashOf(aliceBefore), 2 to bobSynced), after.syncedHashes)
	}

	@Test
	fun `writes no sync journal when the project has none`() {
		writeEntry("person~1~Alice.toml", entry(1, "Alice"))
		writeFile(typeDir(EntryType.PERSON) / "person~1~image.png", TestImages.png())

		migrate()

		assertEquals(listOf("2~Alice.png"), imageFiles())
		assertFalse(ffs.exists(syncPath))
	}

	@Test
	fun `leaves the journal untouched when there is nothing to promote`() {
		writeEntry("person~1~Alice.toml", entry(1, "Alice"))
		val before = syncData(lastId = 1)
		writeSync(before)

		migrate()

		assertEquals(before, readSync())
		assertFalse(ffs.exists(imagesDir))
	}

	@Test
	fun `an entry that cannot be read keeps its image where it is`() {
		val dir = typeDir(EntryType.PERSON)
		ffs.write(dir / "person~1~Alice.toml") { writeUtf8("this is [[ not toml") }
		writeFile(dir / "person~1~image.png", TestImages.png())
		writeEntry("person~2~Bob.toml", entry(2, "Bob"))
		writeFile(dir / "person~2~image.png", TestImages.png())

		migrate()

		assertTrue(ffs.exists(dir / "person~1~image.png"))
		assertEquals(listOf("3~Bob.png"), imageFiles())
	}

	@Test
	fun `an image whose entry is gone becomes an unattached image named after its file`() {
		writeFile(typeDir(EntryType.PERSON) / "person~5~image.gif", TestImages.gif())

		migrate()

		val promoted = imageFiles().single()
		val def = ImagesDatasource.parseFilename(promoted)!!
		assertEquals(1, def.id)
		assertEquals("gif", def.fileExtension)
		assertTrue(ImageFormat.isValidName(def.name), "Unattached image name '${def.name}' must sync")
	}

	@Test
	fun `two images of one entry are both promoted, in listing order`() {
		val alice = writeEntry("person~1~Alice.toml", entry(1, "Alice", images = listOf(40)))
		writeFile(typeDir(EntryType.PERSON) / "person-1-image.jpg", TestImages.jpeg())
		writeFile(typeDir(EntryType.PERSON) / "person~1~image.png", TestImages.png())

		migrate()

		assertEquals(listOf(40, 2, 3), readEntry(alice).images)
		assertEquals(listOf("2~Alice.jpg", "3~Alice.png"), imageFiles().sorted())
	}

	@Test
	fun `keeps images over the size limit and skips extensions an image cannot have`() {
		writeEntry("person~1~Alice.toml", entry(1, "Alice"))
		val big = TestImages.png() + ByteArray((ImageFormat.MAX_IMAGE_BYTES + 1).toInt())
		writeFile(typeDir(EntryType.PERSON) / "person~1~image.png", big)
		writeFile(typeDir(EntryType.PERSON) / "person~1~image.bmp", byteArrayOf(1))

		migrate()

		assertEquals(listOf("2~Alice.png"), imageFiles())
		assertEquals(big.size.toLong(), ffs.metadata(imagesDir / "2~Alice.png").size)
		assertTrue(ffs.exists(typeDir(EntryType.PERSON) / "person~1~image.bmp"))
	}

	@Test
	fun `running twice changes nothing the second time`() {
		val alice = writeEntry("person~1~Alice.toml", entry(1, "Alice"))
		writeFile(typeDir(EntryType.PERSON) / "person~1~image.png", TestImages.png())
		writeSync(syncData(lastId = 1))

		migrate()
		val entryAfterFirst = ffs.read(alice) { readUtf8() }
		val syncAfterFirst = readSync()
		migrate()

		assertEquals(entryAfterFirst, ffs.read(alice) { readUtf8() })
		assertEquals(syncAfterFirst, readSync())
		assertEquals(listOf("2~Alice.png"), imageFiles())
	}

	@Test
	fun `a project without an encyclopedia is left alone`() {
		migrate()

		assertFalse(ffs.exists(imagesDir))
		assertFalse(ffs.exists(projDef.path.toOkioPath() / EncyclopediaDatasource.ENCYCLOPEDIA_DIRECTORY))
	}
}
