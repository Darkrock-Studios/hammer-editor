package repositories.groupmetadata

import com.darkrockstudios.apps.hammer.base.GroupId
import com.darkrockstudios.apps.hammer.common.data.groupmetadata.GroupMetadataDatasource
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import getProjectsDirectory
import okio.Path
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import utils.BaseTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GroupMetadataDatasourceTest : BaseTest() {
	private lateinit var ffs: FakeFileSystem
	private lateinit var datasource: GroupMetadataDatasource
	private lateinit var groupDir: HPath

	private val metadataPath: Path get() = datasource.getMetadataPath(groupDir).toOkioPath()

	@BeforeEach
	override fun setup() {
		super.setup()
		ffs = FakeFileSystem()
		datasource = GroupMetadataDatasource(ffs, createTomlSerializer())
		groupDir = (getProjectsDirectory() / "Trilogy").toHPath()
		ffs.createDirectories(groupDir.toOkioPath())
	}

	@Test
	fun `Created metadata round-trips with its header`() {
		val groupId = GroupId.randomUUID()

		datasource.createMetadata(groupDir, groupId, description = "Three books")

		val loaded = datasource.loadMetadata(groupDir)
		assertEquals(groupId, loaded.info.groupId)
		assertEquals("Three books", loaded.info.description)
		assertFalse(loaded.info.collapsed)
		val firstLine = ffs.read(metadataPath) { readUtf8Line() }
		assertEquals(GroupMetadataDatasource.FILE_HEADER, firstLine)
		assertTrue(datasource.isMarked(groupDir))
	}

	@Test
	fun `A corrupt file is replaced under a fresh id`() {
		ffs.write(metadataPath) { writeUtf8("this is [not toml") }

		val healed = datasource.loadMetadata(groupDir)

		val groupId = assertNotNull(healed.info.groupId)
		assertEquals(groupId, datasource.loadMetadata(groupDir).info.groupId)
	}

	@Test
	fun `A missing file is created on read`() {
		val groupId = assertNotNull(datasource.loadMetadata(groupDir).info.groupId)

		assertTrue(datasource.isMarked(groupDir))
		assertEquals(groupId, datasource.loadMetadata(groupDir).info.groupId)
	}

	@Test
	fun `A file without an id gets one minted and keeps its other fields`() {
		ffs.write(metadataPath) {
			writeUtf8("[info]\ncreated = 2025-01-02T03:04:05Z\ndescription = \"Kept\"\ncollapsed = true\n")
		}

		val groupId = assertNotNull(datasource.loadMetadata(groupDir).info.groupId)

		val stored = datasource.loadMetadata(groupDir)
		assertEquals(groupId, stored.info.groupId)
		assertEquals("Kept", stored.info.description)
		assertTrue(stored.info.collapsed)
	}

	@Test
	fun `Update changes one field and keeps the rest`() {
		val groupId = GroupId.randomUUID()
		datasource.createMetadata(groupDir, groupId, description = "Notes")

		datasource.updateMetadata(groupDir) { it.copy(info = it.info.copy(collapsed = true)) }

		val stored = datasource.loadMetadata(groupDir)
		assertEquals(groupId, stored.info.groupId)
		assertEquals("Notes", stored.info.description)
		assertTrue(stored.info.collapsed)
	}

	@Test
	fun `A blank description is stored as none`() {
		datasource.createMetadata(groupDir, GroupId.randomUUID(), description = "Notes")

		val updated = datasource.updateMetadata(groupDir) { it.copy(info = it.info.copy(description = "  ")) }

		assertNull(updated.info.description)
		assertNull(datasource.loadMetadata(groupDir).info.description)
		assertFalse(ffs.read(metadataPath) { readUtf8() }.contains("\"  \""))
	}

	@Test
	fun `A blank description written by hand reads as none`() {
		ffs.write(metadataPath) {
			writeUtf8("[info]\ngroupId = \"${GroupId.randomUUID().id}\"\ncreated = 2025-01-02T03:04:05Z\ndescription = \" \"\n")
		}

		assertNull(datasource.loadMetadata(groupDir).info.description)
	}

	@Test
	fun `Deleting the metadata unmarks the directory`() {
		datasource.createMetadata(groupDir, GroupId.randomUUID())

		datasource.deleteMetadata(groupDir)

		assertFalse(datasource.isMarked(groupDir))
		assertTrue(ffs.exists(groupDir.toOkioPath()))
	}
}
