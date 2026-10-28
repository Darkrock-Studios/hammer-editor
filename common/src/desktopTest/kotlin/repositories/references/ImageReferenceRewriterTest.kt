package repositories.references

import ENCYCLOPEDIA_ONLY_PROJECT_NAME
import com.darkrockstudios.apps.hammer.base.http.EntityType
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaDatasource
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaRepository
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContainer
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContent
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.data.id.IdAllocator
import com.darkrockstudios.apps.hammer.common.data.references.ImageReferenceRewriter
import com.darkrockstudios.apps.hammer.common.data.references.TouchedEntity
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncJournal
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import createProject
import getProjectDef
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import repositories.encyclopedia.entry1
import repositories.encyclopedia.entry2
import utils.BaseTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ImageReferenceRewriterTest : BaseTest() {

	private val projectDef = getProjectDef(ENCYCLOPEDIA_ONLY_PROJECT_NAME)

	private lateinit var ffs: FakeFileSystem
	private lateinit var syncJournal: SyncJournal
	private lateinit var datasource: EncyclopediaDatasource
	private lateinit var repository: EncyclopediaRepository

	@BeforeEach
	override fun setup() {
		super.setup()
		ffs = FakeFileSystem()
		createProject(ffs, ENCYCLOPEDIA_ONLY_PROJECT_NAME)
		syncJournal = mockk(relaxUnitFun = true)
		every { syncJournal.isServerSynchronized() } returns true
		coEvery { syncJournal.isEntityDirty(any()) } returns false

		setupKoin(module {
			scope<ProjectDefScope> {
				scoped<ProjectDef> { projectDef }
				scoped { repository }
			}
		})
		datasource = EncyclopediaDatasource(projectDef, createTomlSerializer(), ffs)
		repository = EncyclopediaRepository(projectDef, mockk<IdAllocator>(), datasource, syncJournal)
	}

	private suspend fun store(entry: EntryContent) {
		datasource.createEntry(EntryContainer(entry))
	}

	private fun entry(id: Int): EntryContent = repository.loadEntry(id).entry

	private fun touched(vararg ids: Int) = ids.map { TouchedEntity(EntityType.EncyclopediaEntry, it) }.toSet()

	@Test
	fun `remap rewrites entry text and image lists and reports the entries it touched`() = runTest {
		store(entry1().copy(text = "Portrait:\n\n![Alice](hammer://image/5)", images = listOf(5, 6)))
		store(entry2().copy(images = listOf(6, 5)))

		val result = ImageReferenceRewriter(projectDef).remap(mapOf(5 to 50))

		assertEquals(touched(1, 2), result)
		assertEquals("Portrait:\n\n![Alice](hammer://image/50)", entry(1).text)
		assertEquals(listOf(50, 6), entry(1).images)
		assertEquals(listOf(6, 50), entry(2).images)
		coVerify { syncJournal.markEntityAsDirty(1) }
		coVerify { syncJournal.markEntityAsDirty(2) }
	}

	@Test
	fun `entries that do not refer to the image are not rewritten`() = runTest {
		store(entry1().copy(images = listOf(5)))
		val untouchedPath = datasource.getEntryPath(entry2().toDef(projectDef)).toOkioPath()
		val before = ffs.read(untouchedPath) { readUtf8() }

		val result = ImageReferenceRewriter(projectDef).remap(mapOf(5 to 50))

		assertEquals(touched(1), result)
		assertEquals(before, ffs.read(untouchedPath) { readUtf8() })
		coVerify(exactly = 0) { syncJournal.markEntityAsDirty(2) }
	}

	@Test
	fun `removeAll removes the image from text and lists`() = runTest {
		store(entry1().copy(text = "Top\n\n![a](hammer://image/5)\n\nBottom", images = listOf(6, 5)))
		store(entry2().copy(images = listOf(5)))

		val result = ImageReferenceRewriter(projectDef).removeAll(5)

		assertEquals(touched(1, 2), result)
		assertEquals("Top\n\nBottom", entry(1).text)
		assertEquals(listOf(6), entry(1).images)
		assertTrue(entry(2).images.isEmpty())
	}

	@Test
	fun `findUsages lists the entries that show or embed the image`() = runTest {
		store(entry1().copy(text = "![a](hammer://image/5)"))
		store(entry2().copy(images = listOf(5)))

		val usages = ImageReferenceRewriter(projectDef).findUsages(5)

		assertEquals(touched(1, 2), usages.usages.map { it.entity }.toSet())
		assertEquals(setOf("Entry 1", "Entry 2"), usages.usages.map { it.name }.toSet())
		assertTrue(ImageReferenceRewriter(projectDef).findUsages(99).isUnused)
	}

	@Test
	fun `isUsed finds an image that is shown or embedded`() = runTest {
		store(entry1().copy(text = "![a](hammer://image/7)"))
		store(entry2().copy(images = listOf(5)))

		val rewriter = ImageReferenceRewriter(projectDef)

		assertTrue(rewriter.isUsed(5))
		assertTrue(rewriter.isUsed(7))
		assertFalse(rewriter.isUsed(99))
	}

	@Test
	fun `findReferencedIds collects every image entries show or embed`() = runTest {
		store(entry1().copy(text = "![a](hammer://image/7)\n\n![b](hammer://image/5)", images = listOf(5)))
		store(entry2().copy(images = listOf(6, 5)))

		assertEquals(setOf(5, 6, 7), ImageReferenceRewriter(projectDef).findReferencedIds())
	}

	@Test
	fun `an unreadable entry does not stop the others`() = runTest {
		val brokenDir = EncyclopediaDatasource.getTypeDirectory(projectDef, EntryType.PERSON, ffs).toOkioPath()
		ffs.write(brokenDir / "person~3~Broken.toml") { writeUtf8("this is [[ not toml") }
		store(entry2().copy(images = listOf(5)))

		val rewriter = ImageReferenceRewriter(projectDef)

		assertEquals(touched(2), rewriter.remap(mapOf(5 to 50)))
		assertEquals(listOf(50), entry(2).images)
		assertEquals(touched(2), rewriter.findUsages(50).usages.map { it.entity }.toSet())
	}

	@Test
	fun `an entry that fails validation is still rewritten`() = runTest {
		val tooLong = "x".repeat(EncyclopediaRepository.MAX_TAG_SIZE + 1)
		store(entry1().copy(tags = setOf(tooLong), images = listOf(5)))

		assertEquals(touched(1), ImageReferenceRewriter(projectDef).remap(mapOf(5 to 50)))
		assertEquals(listOf(50), entry(1).images)
		assertEquals(setOf(tooLong), entry(1).tags)
	}

	@Test
	fun `remap with no real changes touches nothing`() = runTest {
		store(entry1().copy(images = listOf(5)))

		assertTrue(ImageReferenceRewriter(projectDef).remap(mapOf(5 to 5)).isEmpty())
		assertTrue(ImageReferenceRewriter(projectDef).remap(emptyMap()).isEmpty())
		coVerify(exactly = 0) { syncJournal.markEntityAsDirty(any()) }
	}
}
