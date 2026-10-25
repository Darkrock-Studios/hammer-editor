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

/** Assets a release may legitimately lack; every other listed name must have a digest. */
private val OPTIONAL_ASSETS: Set<String> = setOf("hammer.zip")

/** Suffix of the sidecar each platform job writes next to its installer: `<base64 sha512> <bytes>`. */
const val DIGEST_SUFFIX = ".sha512"

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

/** The one-line sidecar content for an installer. */
fun assetDigestLine(file: File): String = "${sha512Base64(file)} ${file.length()}"

fun parseAssetDigest(name: String, line: String): UpdateAsset {
	val parts = line.trim().split(' ')
	require(parts.size == 2) { "Digest for $name should be '<base64 sha512> <bytes>', got '$line'" }
	val size = parts[1].toLongOrNull() ?: error("Digest for $name has a non-numeric size: '$line'")
	return UpdateAsset(name = name, size = size, sha512 = parts[0])
}

/**
 * Builds every metadata file from the digest sidecars anywhere under [digestsDir], keyed by file
 * name: upload-artifact keeps each installer's format subdirectory. A required installer with no
 * sidecar fails loudly: a yml that omits it would silently leave that format with no update path.
 */
fun updateMetadataFiles(digestsDir: File, version: String, releaseDate: String): Map<String, String> {
	val sidecars = digestsDir.walk()
		.filter { it.isFile && it.name.endsWith(DIGEST_SUFFIX) }
		.groupBy { it.name }
		.mapValues { (name, found) ->
			found.singleOrNull() ?: error("$digestsDir has more than one $name: $found")
		}
	return UPDATE_METADATA_FILES.mapValues { (metadataFile, names) ->
		val assets = names.mapNotNull { name ->
			val sidecar = sidecars[name + DIGEST_SUFFIX]
			when {
				sidecar != null -> parseAssetDigest(name, sidecar.readText())
				name in OPTIONAL_ASSETS -> null
				else -> error("$metadataFile needs $name$DIGEST_SUFFIX, but $digestsDir has no such file")
			}
		}
		renderUpdateMetadata(version, releaseDate, assets)
	}
}

/**
 * Registers the two halves of the update metadata:
 *
 * - `writeAssetDigests -PassetFiles=<path,path>` runs in each platform job right after it
 *   packages and renames its installers, writing `<installer>.sha512` beside each one. The
 *   sidecars travel to the final job as a workflow artifact, so no installer is downloaded
 *   twice and no release lookup is needed.
 * - `writeUpdateMetadata -PreleaseDigestsDir=<dir>` runs once in the final job over the
 *   collected sidecars and writes the yml files to `build/update-metadata/`.
 */
fun Project.registerUpdateMetadataTasks(appVersion: String) {
	tasks.register("writeAssetDigests") {
		group = "distribution"
		description = "Writes a <installer>.sha512 sidecar beside each installer in -PassetFiles (comma separated)."

		doLast {
			val files = findProperty("assetFiles")?.toString()
				?.split(',')
				?.map { it.trim() }
				?.filter { it.isNotEmpty() }
				?.takeIf { it.isNotEmpty() }
				?: error("Pass -PassetFiles=<installer>[,<installer>...]")
			for (path in files) {
				val file = File(path)
				check(file.isFile) { "No installer at $path" }
				val sidecar = File(file.path + DIGEST_SUFFIX)
				sidecar.writeText(assetDigestLine(file))
				println("Wrote $sidecar")
			}
		}
	}

	tasks.register("writeUpdateMetadata") {
		group = "distribution"
		description = "Writes the latest*.yml files the desktop updater reads, from the digest sidecars in -PreleaseDigestsDir."

		doLast {
			val digestsDir = File(
				findProperty("releaseDigestsDir")?.toString()
					?: error("Pass -PreleaseDigestsDir=<directory holding the *$DIGEST_SUFFIX sidecars>")
			)
			val outputDir = layout.buildDirectory.dir("update-metadata").get().asFile
			outputDir.mkdirs()

			val releaseDate = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString()
			for ((name, content) in updateMetadataFiles(digestsDir, appVersion, releaseDate)) {
				val target = outputDir.resolve(name)
				target.writeText(content)
				println("Wrote $target")
			}
		}
	}
}
