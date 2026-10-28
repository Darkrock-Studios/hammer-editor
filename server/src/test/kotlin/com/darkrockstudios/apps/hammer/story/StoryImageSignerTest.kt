package com.darkrockstudios.apps.hammer.story

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.http.createTokenBase64
import com.darkrockstudios.apps.hammer.secret.Keyring
import com.darkrockstudios.apps.hammer.secret.KeyringCodec
import com.darkrockstudios.apps.hammer.secret.KeyringManager
import com.darkrockstudios.apps.hammer.secret.RoleKeys
import com.darkrockstudios.apps.hammer.secret.ServerSecretProvider
import com.darkrockstudios.apps.hammer.utilities.ServerSecretManager
import com.darkrockstudios.apps.hammer.utilities.TokenHasher
import kotlinx.coroutines.test.runTest
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.Test
import java.security.SecureRandom
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class StoryImageSignerTest {

	private val base64 = createTokenBase64()
	private val codec = KeyringCodec(SecureRandom(), base64)
	private val projectId = ProjectId("project-uuid")

	private fun signer(active: String, keys: Map<String, String>): StoryImageSigner {
		val json = codec.serialize(
			Keyring(
				content = RoleKeys("v1", mapOf("v1" to "content-key")),
				tokenHmac = RoleKeys(active, keys),
			)
		)
		val keyring = KeyringManager(object : ServerSecretProvider { override fun loadKeyring() = json }, codec)
		return StoryImageSigner(TokenHasher(keyring, ServerSecretManager(FakeFileSystem(), SecureRandom()), base64))
	}

	private val current = signer("v1", mapOf("v1" to "key-one"))

	@Test
	fun `a signature verifies for the image it was made for`() = runTest {
		val signature = current.sign(projectId, 5)

		assertTrue(current.verify(projectId, 5, signature))
		assertEquals(32, signature.length)
		assertTrue(signature.all { it.isLetterOrDigit() || it == '-' || it == '_' }, signature)
	}

	@Test
	fun `a signature does not carry over to another image or project`() = runTest {
		val signature = current.sign(projectId, 5)

		assertFalse(current.verify(projectId, 6, signature))
		assertFalse(current.verify(ProjectId("other-uuid"), 5, signature))
		assertNotEquals(signature, current.sign(projectId, 6))
	}

	@Test
	fun `a tampered signature fails`() = runTest {
		val signature = current.sign(projectId, 5)
		val flipped = (if (signature[0] == 'A') 'B' else 'A') + signature.drop(1)

		assertFalse(current.verify(projectId, 5, flipped))
		assertFalse(current.verify(projectId, 5, signature.dropLast(1)))
		assertFalse(current.verify(projectId, 5, ""))
	}

	@Test
	fun `a signature from an earlier key generation still verifies`() = runTest {
		val signature = current.sign(projectId, 5)
		val rotated = signer("v2", mapOf("v1" to "key-one", "v2" to "key-two"))

		assertTrue(rotated.verify(projectId, 5, signature))
		assertNotEquals(signature, rotated.sign(projectId, 5))
	}

	@Test
	fun `a signature from a retired key generation fails`() = runTest {
		val signature = current.sign(projectId, 5)
		val retired = signer("v2", mapOf("v2" to "key-two"))

		assertFalse(retired.verify(projectId, 5, signature))
	}
}
