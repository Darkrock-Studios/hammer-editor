package com.darkrockstudios.apps.hammer.plugins

import java.net.JarURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.invariantSeparatorsPathString

/**
 * Fingerprint of the served static assets, stamped onto asset URLs as `?v=` so that changing an
 * asset mints a new URL and the old one can be cached for a year.
 *
 * The stamp is derived from the asset bytes rather than the app version: assets change on ordinary
 * redeploys that don't bump the release version, and a stale stamp on an `immutable` response is
 * unrecoverable from the browser's side. It is stable across restarts of the same build, so a
 * restart doesn't cost every visitor their cached assets.
 */
object AssetVersion {
	private const val ASSET_ROOT = "assets"

	/** Resolving the tree from a file rather than `assets` itself: a jar need not carry directory entries. */
	private const val ASSET_ANCHOR = "assets/css/base.css"
	private const val STAMP_LENGTH = 12

	val stamp: String by lazy { fingerprintAssets() ?: processStartStamp() }

	private fun fingerprintAssets(): String? {
		val digest = MessageDigest.getInstance("SHA-256")
		val anchor = javaClass.classLoader.getResource(ASSET_ANCHOR) ?: return null
		when (anchor.protocol) {
			"file" -> digest.absorbDirectory(assetRoot(anchor) ?: return null)
			"jar" -> digest.absorbJar(anchor)
			else -> return null
		}
		return digest.digest().toHexString().take(STAMP_LENGTH)
	}

	private fun assetRoot(anchor: URL): Path? {
		var directory = Path.of(anchor.toURI()).parent
		while (directory != null && directory.fileName?.toString() != ASSET_ROOT) {
			directory = directory.parent
		}
		return directory
	}

	private fun MessageDigest.absorbDirectory(root: Path) {
		Files.walk(root).use { paths ->
			paths.filter(Files::isRegularFile)
				.sorted(compareBy { root.relativize(it).invariantSeparatorsPathString })
				.forEach { file ->
					absorb(root.relativize(file).invariantSeparatorsPathString, Files.readAllBytes(file))
				}
		}
	}

	private fun MessageDigest.absorbJar(anchor: URL) {
		val connection = (anchor.openConnection() as JarURLConnection).apply { useCaches = false }
		connection.jarFile.use { jar ->
			jar.entries().asSequence()
				.filter { !it.isDirectory && it.name.startsWith("$ASSET_ROOT/") }
				.sortedBy { it.name }
				.forEach { entry ->
					jar.getInputStream(entry).use { absorb(entry.name, it.readBytes()) }
				}
		}
	}

	/** Names take part in the digest so a rename alone still mints a new stamp. */
	private fun MessageDigest.absorb(name: String, content: ByteArray) {
		update(name.toByteArray())
		update(content)
	}

	/**
	 * Used only when the asset tree can't be read — a site that can't serve its own assets. Changes
	 * every restart, which costs cache hits but can never pin a client to a stale response.
	 */
	private fun processStartStamp(): String =
		System.currentTimeMillis().toString(16).takeLast(STAMP_LENGTH)

	private fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }
}
