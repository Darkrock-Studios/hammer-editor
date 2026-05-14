package repositories.references

import com.darkrockstudios.apps.hammer.common.data.references.ReferenceIndex
import com.darkrockstudios.apps.hammer.common.data.references.ReferenceIndexDatasource
import com.darkrockstudios.apps.hammer.common.data.references.ReferenceIndexRepository
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReferenceIndexRepositoryTest : BaseTest() {

	lateinit var ffs: FakeFileSystem
	lateinit var toml: Toml
	lateinit var datasource: ReferenceIndexDatasource

	@BeforeEach
	override fun setup() {
		super.setup()
		ffs = FakeFileSystem()
		toml = createTomlSerializer()
		setupKoin(module {
			single { ffs }
			single { toml }
		})
		datasource = ReferenceIndexDatasource(ffs, toml, getProject1Def())
	}

	private fun createRepo() = ReferenceIndexRepository(getProject1Def(), datasource)

	@Test
	fun `applySceneDelta adds new entry-scene mappings`() = runTest(mainTestDispatcher) {
		datasource.saveIndex(ReferenceIndex(isDirty = false, entryToScenes = emptyMap()))
		val repo = createRepo()
		repo.loadIndex()

		repo.applySceneDelta(sceneId = 100, added = setOf(1, 2), removed = emptySet())

		val saved = datasource.loadIndex()
		assertEquals(setOf(100), saved?.entryToScenes?.get(1))
		assertEquals(setOf(100), saved?.entryToScenes?.get(2))
	}

	@Test
	fun `applySceneDelta removes scene from entry's set`() = runTest(mainTestDispatcher) {
		datasource.saveIndex(
			ReferenceIndex(
				isDirty = false,
				entryToScenes = mapOf(1 to setOf(100, 200)),
			)
		)
		val repo = createRepo()
		repo.loadIndex()

		repo.applySceneDelta(sceneId = 100, added = emptySet(), removed = setOf(1))

		val saved = datasource.loadIndex()
		assertEquals(setOf(200), saved?.entryToScenes?.get(1))
	}

	@Test
	fun `applySceneDelta drops the entry key when its scene set empties`() = runTest(mainTestDispatcher) {
		datasource.saveIndex(
			ReferenceIndex(
				isDirty = false,
				entryToScenes = mapOf(1 to setOf(100)),
			)
		)
		val repo = createRepo()
		repo.loadIndex()

		repo.applySceneDelta(sceneId = 100, added = emptySet(), removed = setOf(1))

		val saved = datasource.loadIndex()
		assertNull(saved?.entryToScenes?.get(1))
	}

	@Test
	fun `applySceneDelta marks dirty when no cache exists`() = runTest(mainTestDispatcher) {
		val repo = createRepo()

		repo.applySceneDelta(sceneId = 100, added = setOf(1), removed = emptySet())
		// markDirty schedules a coroutine on the repository's default dispatcher
		defaultTestDispatcher.scheduler.advanceUntilIdle()

		assertTrue(repo.isDirty.value)
	}

	@Test
	fun `applySceneDelta marks dirty when cache is dirty`() = runTest(mainTestDispatcher) {
		datasource.saveIndex(ReferenceIndex(isDirty = true, entryToScenes = emptyMap()))
		val repo = createRepo()
		repo.loadIndex()

		repo.applySceneDelta(sceneId = 100, added = setOf(1), removed = emptySet())
		defaultTestDispatcher.scheduler.advanceUntilIdle()

		assertTrue(repo.isDirty.value)
	}

	@Test
	fun `markEntryDeleted purges the entry key from forward map`() = runTest(mainTestDispatcher) {
		datasource.saveIndex(
			ReferenceIndex(
				isDirty = false,
				entryToScenes = mapOf(1 to setOf(100), 2 to setOf(200)),
			)
		)
		val repo = createRepo()
		repo.loadIndex()

		repo.markEntryDeleted(1)

		val saved = datasource.loadIndex()
		assertNull(saved?.entryToScenes?.get(1))
		assertEquals(setOf(200), saved?.entryToScenes?.get(2))
	}

	@Test
	fun `markSceneDeleted removes that sceneId from all sets and drops empty keys`() =
		runTest(mainTestDispatcher) {
			datasource.saveIndex(
				ReferenceIndex(
					isDirty = false,
					entryToScenes = mapOf(
						1 to setOf(100, 200),
						2 to setOf(100),
					),
				)
			)
			val repo = createRepo()
			repo.loadIndex()

			repo.markSceneDeleted(100)

			val saved = datasource.loadIndex()
			assertEquals(setOf(200), saved?.entryToScenes?.get(1))
			assertNull(saved?.entryToScenes?.get(2))
		}

	@Test
	fun `markDirty persists the dirty flag across reloads`() = runTest(mainTestDispatcher) {
		datasource.saveIndex(ReferenceIndex(isDirty = false, entryToScenes = mapOf(1 to setOf(100))))
		val repo = createRepo()

		repo.markDirty()
		defaultTestDispatcher.scheduler.advanceUntilIdle()

		val reloaded = datasource.loadIndex()
		assertTrue(reloaded?.isDirty == true)
	}

	@Test
	fun `clearDirty resets dirty state`() = runTest(mainTestDispatcher) {
		datasource.saveIndex(ReferenceIndex(isDirty = true, entryToScenes = emptyMap()))
		val repo = createRepo()
		repo.loadIndex()

		assertTrue(repo.isDirty.value)
		repo.clearDirty()
		assertEquals(false, repo.isDirty.value)
	}
}
