package com.darkrockstudios.apps.hammer.story

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.utilities.TokenHasher
import java.security.MessageDigest

/**
 * Signs the image links a public story page emits. A valid signature proves the server rendered
 * that image's reference into prose the reader was allowed to see, so serving the image never
 * has to decrypt scenes to look for the reference again.
 *
 * Signatures do not expire: a link stays good for as long as its key generation is in the
 * keyring. Access to the story itself is still checked on every image request.
 */
class StoryImageSigner(
	private val tokenHasher: TokenHasher,
) {
	suspend fun sign(projectId: ProjectId, imageId: Int): String =
		urlSafe(tokenHasher.hashToken(message(projectId, imageId)))

	/**
	 * Identifies the key [sign] uses without revealing it. A page carrying signed links has to
	 * fold this into its validator, or a reader could keep links from a retired generation.
	 */
	fun keyGeneration(): String = tokenHasher.activeKeyGeneration()

	/** True when [signature] was made by [sign] under any key generation still in the keyring. */
	suspend fun verify(projectId: ProjectId, imageId: Int, signature: String): Boolean {
		val given = signature.toByteArray(Charsets.UTF_8)
		return tokenHasher.hashTokenWithEveryKey(message(projectId, imageId))
			.map { urlSafe(it).toByteArray(Charsets.UTF_8) }
			.fold(false) { matched, expected -> MessageDigest.isEqual(expected, given) or matched }
	}

	private fun message(projectId: ProjectId, imageId: Int) = "$MESSAGE_PREFIX:${projectId.id}:$imageId"

	// The hasher's alphabet is not this class's to rely on; the signature has to sit in a query
	// string unescaped either way.
	private fun urlSafe(hash: String): String = hash
		.replace('+', '-')
		.replace('/', '_')
		.trimEnd('=')
		.take(SIGNATURE_LENGTH)

	private companion object {
		/** Keeps these MACs apart from every other use of the token key. */
		const val MESSAGE_PREFIX = "story-image"
		const val SIGNATURE_LENGTH = 32
	}
}
