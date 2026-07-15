package components.projecthome

import com.darkrockstudios.apps.hammer.common.components.projecthome.ImportStoryUseCase
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.importer.ImportPreview
import com.darkrockstudios.apps.hammer.common.data.importer.PreviewItem
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorService
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ImportStoryUseCaseTest {

	private val service: SceneEditorService = mockk(relaxed = true)

	private fun sceneItem(id: Int): SceneItem = mockk { every { this@mockk.id } returns id }

	private val scenePath = HPath("/scene", "scene", false)

	@Test
	fun `empty preview creates nothing`() = runTest {
		val created = ImportStoryUseCase(service).execute(ImportPreview(emptyList()))

		assertEquals(0, created)
		coVerify(exactly = 0) { service.createScene(any(), any(), any(), any()) }
	}

	@Test
	fun `scene with content is created and stored`() = runTest {
		val item = sceneItem(10)
		coEvery { service.createScene(null, "Chapter 1") } returns item
		every { service.resolveScenePathFromFilesystem(10) } returns scenePath
		coEvery { service.storeSceneMarkdownRaw(any(), scenePath) } returns true

		val preview = ImportPreview(listOf(PreviewItem.Scene(name = "Chapter 1", markdown = "Once upon a time")))
		val created = ImportStoryUseCase(service).execute(preview)

		assertEquals(1, created)
		coVerify {
			service.storeSceneMarkdownRaw(
				match { it.scene === item && it.markdown == "Once upon a time" },
				scenePath,
			)
		}
	}

	@Test
	fun `scene with empty markdown is created but not stored`() = runTest {
		coEvery { service.createScene(null, "Empty") } returns sceneItem(11)

		val preview = ImportPreview(listOf(PreviewItem.Scene(name = "Empty", markdown = "")))
		val created = ImportStoryUseCase(service).execute(preview)

		assertEquals(1, created)
		coVerify(exactly = 0) { service.storeSceneMarkdownRaw(any(), any()) }
	}

	@Test
	fun `scene that fails to create is not counted`() = runTest {
		coEvery { service.createScene(null, "Doomed") } returns null

		val preview = ImportPreview(listOf(PreviewItem.Scene(name = "Doomed", markdown = "text")))
		val created = ImportStoryUseCase(service).execute(preview)

		assertEquals(0, created)
	}

	@Test
	fun `scene with no resolvable path is not counted`() = runTest {
		coEvery { service.createScene(null, "Lost") } returns sceneItem(12)
		every { service.resolveScenePathFromFilesystem(12) } returns null

		val preview = ImportPreview(listOf(PreviewItem.Scene(name = "Lost", markdown = "text")))
		val created = ImportStoryUseCase(service).execute(preview)

		assertEquals(0, created)
	}

	@Test
	fun `scene whose store fails is not counted`() = runTest {
		coEvery { service.createScene(null, "Flaky") } returns sceneItem(13)
		every { service.resolveScenePathFromFilesystem(13) } returns scenePath
		coEvery { service.storeSceneMarkdownRaw(any(), scenePath) } returns false

		val preview = ImportPreview(listOf(PreviewItem.Scene(name = "Flaky", markdown = "text")))
		val created = ImportStoryUseCase(service).execute(preview)

		assertEquals(0, created)
	}

	@Test
	fun `group children are created under the new group`() = runTest {
		val group = sceneItem(20)
		coEvery { service.createGroup(null, "Act 1") } returns group
		coEvery { service.createScene(group, "Scene A") } returns sceneItem(21)
		coEvery { service.createScene(group, "Scene B") } returns sceneItem(22)

		val preview = ImportPreview(
			listOf(
				PreviewItem.Group(
					name = "Act 1",
					scenes = listOf(
						PreviewItem.Scene("Scene A", markdown = ""),
						PreviewItem.Scene("Scene B", markdown = ""),
					),
				)
			)
		)
		val created = ImportStoryUseCase(service).execute(preview)

		assertEquals(2, created)
		coVerify { service.createScene(group, "Scene A") }
		coVerify { service.createScene(group, "Scene B") }
	}

	@Test
	fun `group that fails to create skips its children`() = runTest {
		coEvery { service.createGroup(null, "Act 1") } returns null

		val preview = ImportPreview(
			listOf(
				PreviewItem.Group(
					name = "Act 1",
					scenes = listOf(PreviewItem.Scene("Scene A", markdown = "")),
				)
			)
		)
		val created = ImportStoryUseCase(service).execute(preview)

		assertEquals(0, created)
		coVerify(exactly = 0) { service.createScene(any(), any(), any(), any()) }
	}
}
