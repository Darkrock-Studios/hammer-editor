package repositories.references

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.references.ReferenceIndex
import com.darkrockstudios.apps.hammer.common.data.references.ReferenceIndexDatasource
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import getProject1Def
import kotlinx.coroutines.test.runTest
import net.peanuuutz.tomlkt.Toml
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import utils.BaseTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

class ReferenceIndexDatasourceTest : BaseTest() {

	lateinit var ffs: FakeFileSystem
	lateinit var toml: Toml

	@BeforeEach
	override fun setup() {
		super.setup()
		ffs = FakeFileSystem()
		toml = createTomlSerializer()
		setupKoin(module {
			single { ffs }
			single { toml }
		})
	}

	private fun createDatasource(projectDef: ProjectDef = getProject1Def()) =
		ReferenceIndexDatasource(ffs, toml, projectDef)

	@Test
	fun `Load returns null when cache file is missing`() = runTest(mainTestDispatcher) {
		val datasource = createDatasource()
		assertNull(datasource.loadIndex())
		assertFalse(datasource.exists())
	}

	@Test
	fun `Save then load round-trips the index`() = runTest(mainTestDispatcher) {
		val datasource = createDatasource()
		val original = ReferenceIndex(
			isDirty = false,
			lastCalculated = Clock.System.now(),
			entryToScenes = mapOf(1 to setOf(10, 11), 2 to setOf(11, 12)),
		)

		datasource.saveIndex(original)
		assertTrue(datasource.exists())

		val loaded = datasource.loadIndex()
		assertEquals(original.entryToScenes, loaded?.entryToScenes)
		assertEquals(original.isDirty, loaded?.isDirty)
		assertEquals(original.schemaVersion, loaded?.schemaVersion)
	}

	@Test
	fun `Load returns null when cache file is corrupt`() = runTest(mainTestDispatcher) {
		val datasource = createDatasource()
		datasource.saveIndex(ReferenceIndex())

		val cachePath = ffs.allPaths.first { it.name == ReferenceIndexDatasource.FILENAME }
		ffs.write(cachePath) { writeUtf8("not valid toml @@@@") }

		assertNull(datasource.loadIndex())
	}

	@Test
	fun `Delete removes the cache file`() = runTest(mainTestDispatcher) {
		val datasource = createDatasource()
		datasource.saveIndex(ReferenceIndex())
		assertTrue(datasource.exists())

		datasource.delete()
		assertFalse(datasource.exists())
	}
}
