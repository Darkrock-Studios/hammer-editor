package repositories.references

import PROJECT_1_NAME
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.SceneSummary
import com.darkrockstudios.apps.hammer.common.data.references.ReferenceIndex
import com.darkrockstudios.apps.hammer.common.data.references.ReferenceIndexDatasource
import com.darkrockstudios.apps.hammer.common.data.references.ReferenceIndexRepository
import com.darkrockstudios.apps.hammer.common.data.references.SceneMetadataReferenceRemapper
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.scenemetadata.SceneMetadata
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.scenemetadata.SceneMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.tree.ImmutableTree
import com.darkrockstudios.apps.hammer.common.data.tree.TreeValue
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import createProject
import getProject1Def
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import net.peanuuutz.tomlkt.Toml
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import utils.BaseTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SceneMetadataReferenceRemapperTest : BaseTest() {

	private val projectDef: ProjectDef = getProject1Def()
	private lateinit var ffs: FakeFileSystem
	private lateinit var toml: Toml
	private lateinit var metadataDatasource: SceneMetadataDatasource
	private lateinit var sceneEditor: SceneEditorRepository
	private lateinit var indexDatasource: ReferenceIndexDatasource
	private lateinit var indexRepository: ReferenceIndexRepository

	@BeforeEach
	override fun setup() {
		super.setup()
		ffs = FakeFileSystem()
		toml = createTomlSerializer()
		setupKoin(module {
			single { ffs }
			single { toml }
		})
		createProject(ffs, PROJECT_1_NAME)
		metadataDatasource = SceneMetadataDatasource(ffs, toml, projectDef)
		indexDatasource = ReferenceIndexDatasource(ffs, toml, projectDef)
		indexRepository = ReferenceIndexRepository(projectDef, indexDatasource)
		sceneEditor = mockk(relaxed = true)
	}

	private fun stubSceneTree(activeIds: List<Int>, archivedIds: List<Int> = emptyList()) {
		val children = activeIds.mapIndexed { idx, id ->
			TreeValue(
				value = SceneItem(projectDef, SceneItem.Type.Scene, id, "S$id", id),
				index = idx + 1,
				parent = 0,
				children = emptyList(),
				depth = 1,
				totalChildren = 0,
			)
		}
		val root = TreeValue(
			value = SceneItem(projectDef, SceneItem.Type.Root, SceneItem.ROOT_ID, "", 0),
			index = 0,
			parent = -1,
			children = children,
			depth = 0,
			totalChildren = children.size,
		)
		val summary = SceneSummary(ImmutableTree(root, totalChildren = children.size + 1), emptySet())
		val flow = MutableSharedFlow<SceneSummary>(
			replay = 1, extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST,
		).apply { tryEmit(summary) }
		every { sceneEditor.sceneListChannel } returns flow
		coEvery { sceneEditor.getArchivedScenes() } returns archivedIds.map {
			SceneItem(projectDef, SceneItem.Type.Scene, it, "A$it", it, archived = true)
		}
	}

	private fun makeRemapper() = SceneMetadataReferenceRemapper(
		sceneEditorRepository = sceneEditor,
		sceneMetadataDatasource = metadataDatasource,
		referenceIndexRepository = indexRepository,
	)

	@Test
	fun `remap rewrites confirmed references that contain the old id`() = runTest(mainTestDispatcher) {
		stubSceneTree(activeIds = listOf(10, 11))
		metadataDatasource.storeMetadata(SceneMetadata(confirmedReferences = setOf(7, 9)), 10)
		metadataDatasource.storeMetadata(SceneMetadata(confirmedReferences = setOf(7)), 11)

		makeRemapper().remapEntryReferences(oldEntryId = 7, newEntryId = 42)

		assertEquals(setOf(9, 42), metadataDatasource.loadMetadata(10)?.confirmedReferences)
		assertEquals(setOf(42), metadataDatasource.loadMetadata(11)?.confirmedReferences)
	}

	@Test
	fun `remap rewrites dismissed references too`() = runTest(mainTestDispatcher) {
		stubSceneTree(activeIds = listOf(10))
		metadataDatasource.storeMetadata(SceneMetadata(dismissedReferences = setOf(7)), 10)

		makeRemapper().remapEntryReferences(oldEntryId = 7, newEntryId = 42)

		assertEquals(setOf(42), metadataDatasource.loadMetadata(10)?.dismissedReferences)
	}

	@Test
	fun `remap walks archived scenes too`() = runTest(mainTestDispatcher) {
		stubSceneTree(activeIds = listOf(10), archivedIds = listOf(99))
		metadataDatasource.storeMetadata(SceneMetadata(confirmedReferences = setOf(7)), 99)

		makeRemapper().remapEntryReferences(oldEntryId = 7, newEntryId = 42)

		assertEquals(setOf(42), metadataDatasource.loadMetadata(99)?.confirmedReferences)
	}

	@Test
	fun `remap leaves untouched scenes alone`() = runTest(mainTestDispatcher) {
		stubSceneTree(activeIds = listOf(10, 11))
		metadataDatasource.storeMetadata(SceneMetadata(confirmedReferences = setOf(7)), 10)
		metadataDatasource.storeMetadata(SceneMetadata(confirmedReferences = setOf(99)), 11)

		makeRemapper().remapEntryReferences(oldEntryId = 7, newEntryId = 42)

		assertEquals(setOf(99), metadataDatasource.loadMetadata(11)?.confirmedReferences)
	}

	@Test
	fun `remap marks the index dirty when at least one scene was rewritten`() =
		runTest(mainTestDispatcher) {
			stubSceneTree(activeIds = listOf(10))
			metadataDatasource.storeMetadata(SceneMetadata(confirmedReferences = setOf(7)), 10)
			indexDatasource.saveIndex(ReferenceIndex(isDirty = false))
			indexRepository.loadIndex()

			makeRemapper().remapEntryReferences(oldEntryId = 7, newEntryId = 42)
			defaultTestDispatcher.scheduler.advanceUntilIdle()

			assertTrue(indexRepository.isDirty.value)
		}

	@Test
	fun `remap is a no-op when no scene contains the old id`() = runTest(mainTestDispatcher) {
		stubSceneTree(activeIds = listOf(10))
		metadataDatasource.storeMetadata(SceneMetadata(confirmedReferences = setOf(99)), 10)
		indexDatasource.saveIndex(ReferenceIndex(isDirty = false))
		indexRepository.loadIndex()

		makeRemapper().remapEntryReferences(oldEntryId = 7, newEntryId = 42)
		defaultTestDispatcher.scheduler.advanceUntilIdle()

		assertEquals(setOf(99), metadataDatasource.loadMetadata(10)?.confirmedReferences)
		assertEquals(false, indexRepository.isDirty.value)
	}

	@Test
	fun `remap is a no-op when oldId equals newId`() = runTest(mainTestDispatcher) {
		stubSceneTree(activeIds = listOf(10))
		metadataDatasource.storeMetadata(SceneMetadata(confirmedReferences = setOf(7)), 10)

		makeRemapper().remapEntryReferences(oldEntryId = 7, newEntryId = 7)

		assertEquals(setOf(7), metadataDatasource.loadMetadata(10)?.confirmedReferences)
	}
}
