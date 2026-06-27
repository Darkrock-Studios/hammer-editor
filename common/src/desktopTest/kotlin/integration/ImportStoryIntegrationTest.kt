package integration

import com.darkrockstudios.apps.hammer.common.components.projecthome.ImportStoryUseCase
import com.darkrockstudios.apps.hammer.common.data.importer.ImportPreview
import com.darkrockstudios.apps.hammer.common.data.importer.PreviewItem
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
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
