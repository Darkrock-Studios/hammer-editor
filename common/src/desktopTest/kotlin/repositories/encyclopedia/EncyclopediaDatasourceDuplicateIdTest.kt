package repositories.encyclopedia

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import testProjectId
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaDatasource
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContainer
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContent
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okio.Path
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import utils.BaseTest
import kotlin.test.assertEquals

/**
 * File sync tools leave copies of entry files beside them, e.g. a Syncthing conflict copy, which
 * still matches the entry filename pattern. Only one file per entry id may load.
 */
class EncyclopediaDatasourceDuplicateIdTest : BaseTest() {

	private lateinit var ffs: FakeFileSystem
	private lateinit var datasource: EncyclopediaDatasource
	private lateinit var personDir: Path

	private val projectDef = ProjectDef(
		id = testProjectId("Test Project"),
		name = "Test Project",
		path = "/projects/Test Project".toPath().toHPath(),
	)

	private val bob = EntryContent(
		id = 5,
		name = "Bob",
		type = EntryType.PERSON,
		text = "The real Bob",
		tags = emptySet(),
	)

	@BeforeEach
	override fun setup() {
		super.setup()
		ffs = FakeFileSystem()
		ffs.createDirectories(projectDef.path.toOkioPath())
		datasource = EncyclopediaDatasource(projectDef, createTomlSerializer(), ffs)
		personDir = projectDef.path.toOkioPath() / "encyclopedia" / "person"
	}

	@AfterEach
	override fun tearDown() {
		super.tearDown()
		ffs.checkNoOpenFiles()
	}

	private suspend fun writeBobWithSyncCopies() {
		datasource.createEntry(EntryContainer(bob))
		ffs.write(personDir / "person~5~Bob.sync-conflict-20261024-173944-53PQXWR.toml") { writeUtf8("other copy") }
		ffs.write(personDir / ".syncthing.person~5~Bob.toml.tmp") { writeUtf8("partial") }
	}

	@Test
	fun `Load entries keeps only the original of a copied entry`() = runTest {
		writeBobWithSyncCopies()

		val entries = datasource.loadEntriesImperative()

		assertEquals(1, entries.size)
		assertEquals("Bob", entries.single().name)
	}

	@Test
	fun `Find entry path by id returns the original of a copied entry`() = runTest {
		writeBobWithSyncCopies()

		assertEquals("person~5~Bob.toml", datasource.findEntryPath(5)?.name)
		assertEquals("The real Bob", datasource.loadEntry(datasource.getEntryPath(5)).entry.text)
	}
}
