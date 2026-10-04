package com.darkrockstudios.build

import org.gradle.api.Project
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64

/** One attached installer as the update metadata describes it. */
data class UpdateAsset(val name: String, val size: Long, val sha512: String)

/**
 * The metadata files the desktop updater reads, each listing its platform's installers in
 * preference order. The format is electron-builder's `latest.yml`, which Nucleus parses; the
 * `url` is the bare asset name and the client prefixes the release URL.
 */
private val UPDATE_METADATA_FILES: Map<String, List<String>> = mapOf(
	"latest.yml" to listOf("hammer.msi", "hammer.exe"),
	"latest-mac.yml" to listOf("hammer.zip", "hammer.dmg", "hammer.pkg"),
	"latest-linux.yml" to listOf("hammer.deb", "hammer.rpm", "hammer.AppImage"),
)

/** Assets a release may legitimately lack; every other listed name must be attached. */
private val OPTIONAL_ASSETS: Set<String> = setOf("hammer.zip")

private const val SHA512_BUFFER_SIZE = 1 shl 16

fun renderUpdateMetadata(version: String, releaseDate: String, assets: List<UpdateAsset>): String =
	buildString {
		appendLine("version: $version")
		appendLine("releaseDate: $releaseDate")
		appendLine("files:")
		for (asset in assets) {
			appendLine("  - url: ${asset.name}")
			appendLine("    sha512: ${asset.sha512}")
			appendLine("    size: ${asset.size}")
		}
	}

/** Base64 of the SHA-512 digest, which is how Nucleus's checksum verifier compares it. */
fun sha512Base64(file: File): String {
	val digest = MessageDigest.getInstance("SHA-512")
	file.inputStream().use { input ->
		val buffer = ByteArray(SHA512_BUFFER_SIZE)
		while (true) {
			val read = input.read(buffer)
			if (read < 0) break
			digest.update(buffer, 0, read)
		}
	}
	return Base64.getEncoder().encodeToString(digest.digest())
}

/**
 * Builds every metadata file from the installers in [assetsDir], keyed by file name. A required
 * installer that is missing fails loudly: a yml that omits it would silently leave that format
 * with no update path.
 */
fun updateMetadataFiles(assetsDir: File, version: String, releaseDate: String): Map<String, String> =
	UPDATE_METADATA_FILES.mapValues { (metadataFile, names) ->
		val assets = names.mapNotNull { name ->
			val file = assetsDir.resolve(name)
			when {
				file.isFile -> UpdateAsset(name, file.length(), sha512Base64(file))
				name in OPTIONAL_ASSETS -> null
				else -> error("$metadataFile needs $name, but $assetsDir has no such file")
			}
		}
		renderUpdateMetadata(version, releaseDate, assets)
	}

/**
 * Registers `writeUpdateMetadata`, which the release workflow runs once every installer is
 * attached: `-PreleaseAssetsDir` points at a directory holding the downloaded assets, and the
 * yml files land in `build/update-metadata/`.
 */
fun Project.registerUpdateMetadataTask(appVersion: String) {
	tasks.register("writeUpdateMetadata") {
		group = "distribution"
		description = "Writes the latest*.yml files the desktop updater reads, from the installers in -PreleaseAssetsDir."

		doLast {
			val assetsDir = File(
				findProperty("releaseAssetsDir")?.toString()
					?: error("Pass -PreleaseAssetsDir=<directory holding the release assets>")
			)
			val outputDir = layout.buildDirectory.dir("update-metadata").get().asFile
			outputDir.mkdirs()

			val releaseDate = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString()
			for ((name, content) in updateMetadataFiles(assetsDir, appVersion, releaseDate)) {
				val target = outputDir.resolve(name)
				target.writeText(content)
				println("Wrote $target")
			}
		}
	}
}
