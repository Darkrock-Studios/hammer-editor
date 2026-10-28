package com.darkrockstudios.apps.hammer.project.synchronizers

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.image.ImageFormat
import com.darkrockstudios.apps.hammer.project.EntityTooLargeException
import com.darkrockstudios.apps.hammer.project.EntityValidationException
import com.darkrockstudios.apps.hammer.utilities.SResult
import com.darkrockstudios.apps.hammer.utilities.isSuccess
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import korlibs.crypto.encoding.Base64
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.serializer
import org.junit.jupiter.api.Test
import kotlin.reflect.KClass
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(InternalSerializationApi::class)
class ServerImageSynchronizerTest :
	ServerEntitySynchronizerTest<ApiProjectEntity.ImageEntity, ServerImageSynchronizer>() {

	override val entityType: ApiProjectEntity.Type = ApiProjectEntity.Type.IMAGE
	override val entityClazz: KClass<ApiProjectEntity.ImageEntity> = ApiProjectEntity.ImageEntity::class
	override val pathStub: String = "image"

	override fun createSynchronizer(): ServerImageSynchronizer = ServerImageSynchronizer(datasource)

	override fun createNewEntity(): ApiProjectEntity.ImageEntity = image(png(1))

	override fun createExistingEntity(): ApiProjectEntity.ImageEntity =
		image(png(2)).copy(name = "Existing Map")

	private fun png(marker: Int): ByteArray =
		byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, marker.toByte())

	private fun image(bytes: ByteArray, extension: String = "png", name: String = "Map") =
		ApiProjectEntity.ImageEntity(
			id = 1,
			name = name,
			fileExtension = extension,
			base64 = Base64.encode(bytes, url = true),
		)

	private suspend fun save(entity: ApiProjectEntity.ImageEntity): SResult<Unit> {
		coEvery {
			datasource.loadEntityHash(any(), any(), any())
		} returns SResult.failure(IllegalStateException("not found"))
		coEvery {
			datasource.storeEntity(any(), any(), any(), any(), entityClazz.serializer())
		} returns SResult.success(Unit)
		return createSynchronizer().saveEntity(1L, mockk(), entity, null, false)
	}

	private fun assertNotStored() {
		coVerify(exactly = 0) { datasource.storeEntity(any(), any(), any(), any(), entityClazz.serializer()) }
	}

	@Test
	fun `Accepts every allowed format`() = runTest {
		val samples = mapOf(
			"png" to png(0),
			"jpg" to byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()),
			"jpeg" to byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()),
			"webp" to "RIFF\u0000\u0000\u0000\u0000WEBPVP8 ".encodeToByteArray(),
			"gif" to "GIF89a".encodeToByteArray(),
			"svg" to "<svg xmlns=\"http://www.w3.org/2000/svg\"/>".encodeToByteArray(),
		)
		for ((extension, bytes) in samples) {
			val result = save(image(bytes, extension))
			assertTrue(isSuccess(result), extension)
		}
	}

	@Test
	fun `Rejects an image over the cap as too large`() = runTest {
		val bytes = png(0) + ByteArray(ImageFormat.MAX_IMAGE_BYTES.toInt())
		val result = save(image(bytes))

		assertFalse(isSuccess(result))
		assertIs<EntityTooLargeException>(result.exception)
		assertNotStored()
	}

	@Test
	fun `Rejects an unsupported extension`() = runTest {
		val result = save(image(png(0), extension = "exe"))

		assertFalse(isSuccess(result))
		assertIs<EntityValidationException>(result.exception)
		assertNotStored()
	}

	@Test
	fun `Rejects bytes that do not match the extension`() = runTest {
		val result = save(image("not an image".encodeToByteArray(), extension = "png"))

		assertFalse(isSuccess(result))
		assertIs<EntityValidationException>(result.exception)
		assertNotStored()
	}

	@Test
	fun `Rejects data that is not base64`() = runTest {
		val result = save(image(png(0)).copy(base64 = "%%%not base64%%%"))

		assertFalse(isSuccess(result))
		assertIs<EntityValidationException>(result.exception)
		assertNotStored()
	}

	@Test
	fun `Rejects a name that cannot be stored as a filename`() = runTest {
		for (name in listOf("", "  ", "bad~name", "trailing.", "Map\uA789North")) {
			val result = save(image(png(0), name = name))
			assertFalse(isSuccess(result), name)
			assertIs<EntityValidationException>(result.exception, name)
		}
		assertNotStored()
	}
}
