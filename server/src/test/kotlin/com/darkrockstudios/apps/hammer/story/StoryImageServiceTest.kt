package com.darkrockstudios.apps.hammer.story

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.project.EntityNotFound
import com.darkrockstudios.apps.hammer.project.ProjectDefinition
import com.darkrockstudios.apps.hammer.project.ProjectEntityDatasource
import com.darkrockstudios.apps.hammer.utilities.SResult
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.KSerializer
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class StoryImageServiceTest {

	private lateinit var datasource: ProjectEntityDatasource
	private lateinit var service: StoryImageService

	private val userId = 1L
	private val imageId = 7
	private val projectDef = ProjectDefinition(name = "Test Story", uuid = ProjectId("test-project-uuid"))

	@BeforeEach
	fun setup() {
		datasource = mockk()
		service = StoryImageService(datasource)
	}

	@Test
	fun `imageHash is null when the id is not an image`() = runTest {
		coEvery {
			datasource.loadEntityHash(userId, projectDef, imageId, ApiProjectEntity.Type.IMAGE)
		} returns SResult.failure(EntityNotFound(imageId))

		assertNull(service.imageHash(userId, projectDef, imageId))
	}

	@Test
	fun `imageHash returns the stored hash`() = runTest {
		coEvery {
			datasource.loadEntityHash(userId, projectDef, imageId, ApiProjectEntity.Type.IMAGE)
		} returns SResult.success("abc123")

		assertEquals("abc123", service.imageHash(userId, projectDef, imageId))
	}

	@Test
	fun `loadImage is null when the image is missing`() = runTest {
		coEvery {
			datasource.loadEntity(userId, projectDef, imageId, ApiProjectEntity.Type.IMAGE, any<KSerializer<ApiProjectEntity.ImageEntity>>())
		} returns SResult.failure(EntityNotFound(imageId))

		assertNull(service.loadImage(userId, projectDef, imageId))
	}

	@Test
	fun `loadImage is null for an unknown extension`() = runTest {
		stubImage(fileExtension = "bmp", base64 = "AAAA")

		assertNull(service.loadImage(userId, projectDef, imageId))
	}

	@Test
	fun `loadImage returns the media type and bytes`() = runTest {
		stubImage(fileExtension = "png", base64 = "AQID")

		val image = assertNotNull(service.loadImage(userId, projectDef, imageId))
		assertEquals("image/png", image.mediaType)
		assertContentEquals(byteArrayOf(1, 2, 3), image.bytes)
	}

	private fun stubImage(fileExtension: String, base64: String) {
		coEvery {
			datasource.loadEntity(userId, projectDef, imageId, ApiProjectEntity.Type.IMAGE, any<KSerializer<ApiProjectEntity.ImageEntity>>())
		} returns SResult.success(
			ApiProjectEntity.ImageEntity(
				id = imageId,
				name = "Map",
				fileExtension = fileExtension,
				base64 = base64,
			)
		)
	}
}
