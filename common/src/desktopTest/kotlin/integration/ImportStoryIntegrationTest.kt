package integration

import com.darkrockstudios.apps.hammer.common.components.projecthome.ImportStoryUseCase
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.importer.ImportPreview
import com.darkrockstudios.apps.hammer.common.data.importer.PreviewItem
import com.darkrockstudios.apps.hammer.common.data.tree.ImmutableTree
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ImportStoryIntegrationTest : BaseIntegrationTest() {

	@Test
	fun `importing twelve chapter groups crosses the order-padding boundary without error`() = runTest {
		configureProject("Empty Project")
		ffs.createDirectories(sceneDatasource.getSceneDirectory().toOkioPath())
		sceneEditorRepository.initializeSceneEditor()
		val useCase = ImportStoryUseCase(sceneEditorService)

		val items = (1..12).map { n ->
			PreviewItem.Group(
				name = "Chapter $n",
				scenes = listOf(PreviewItem.Scene(name = "Chapter $n", markdown = "Body of chapter $n.")),
			)
		}

		val created = useCase.execute(ImportPreview(items))

		assertEquals(12, created)
	}

	@Test
	fun `an import emits the scene tree once, not once per scene`() = runTest {
		configureProject("Empty Project")
		ffs.createDirectories(sceneDatasource.getSceneDirectory().toOkioPath())
		sceneEditorRepository.initializeSceneEditor()
		val useCase = ImportStoryUseCase(sceneEditorService)

		val emissions = mutableListOf<ImmutableTree<SceneItem>>()
		val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
			sceneEditorRepository.sceneTreeUpdates.collect { emissions.add(it) }
		}
		runCurrent()
		// The flow replays the tree from initialization; only count what the import itself emits.
		emissions.clear()

		val items = (1..8).map { n ->
			PreviewItem.Scene(name = "Scene $n", markdown = "Body of scene $n.")
		}
		val created = useCase.execute(ImportPreview(items))
		collector.cancel()

		assertEquals(8, created)
		assertEquals(1, emissions.size, "An import should emit the tree once, after every scene exists")
		assertEquals(8, emissions.single().totalChildren, "The single emission must carry the final tree")
	}

	@Test
	fun `importing groups with typographic apostrophes succeeds`() = runTest {
		configureProject("Empty Project")
		ffs.createDirectories(sceneDatasource.getSceneDirectory().toOkioPath())
		sceneEditorRepository.initializeSceneEditor()
		val useCase = ImportStoryUseCase(sceneEditorService)

		val items = (1..12).map { n ->
			PreviewItem.Group(
				name = "CHAPTER $n. The Mock Turtle’s Story",
				scenes = listOf(PreviewItem.Scene(name = "The Mock Turtle’s Story", markdown = "Prose.")),
			)
		}

		val created = useCase.execute(ImportPreview(items))

		assertEquals(12, created)
	}
}
