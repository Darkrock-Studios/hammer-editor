package com.darkrockstudios.build

import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Updates the Flatpak manifest and metainfo files with the new version and JVM version.
 *
 * @param newVersion The new app version (e.g., "1.12.1")
 * @param jvmVersion The JVM version to use (e.g., "21")
 * @param manifestFile The Flatpak manifest YAML file to update
 * @param metainfoFile The AppStream metainfo XML file to update
 * @param changelog The changelog text to include in the release description
 */
fun updateFlatpakFiles(newVersion: SemVar, jvmVersion: String, manifestFile: File, metainfoFile: File, changelog: String) {
	updateFlatpakManifest(jvmVersion, manifestFile)
	updateFlatpakMetainfo(newVersion, metainfoFile, changelog)
}

private fun updateFlatpakManifest(jvmVersion: String, manifestFile: File) {
	if (!manifestFile.exists()) {
		println("Warning: Flatpak manifest not found at ${manifestFile.absolutePath}, skipping update")
		return
	}

	var content = manifestFile.readText()

	// Update openjdkXX references in sdk-extensions
	content = content.replace(
		Regex("""org\.freedesktop\.Sdk\.Extension\.openjdk\d+"""),
		"org.freedesktop.Sdk.Extension.openjdk$jvmVersion"
	)

	// Update openjdk-XX path references
	content = content.replace(
		Regex("""openjdk-\d+"""),
		"openjdk-$jvmVersion"
	)

	manifestFile.writeText(content)
	println("Flatpak manifest updated with JVM $jvmVersion")
}

private fun updateFlatpakMetainfo(newVersion: SemVar, metainfoFile: File, changelog: String) {
	if (!metainfoFile.exists()) {
		println("Warning: Flatpak metainfo not found at ${metainfoFile.absolutePath}, skipping update")
		return
	}

	var content = metainfoFile.readText()

	// Get today's date for the release
	val releaseDate = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

	// Check if this version already exists in releases
	val versionStr = newVersion.toString()
	if (content.contains("""version="$versionStr"""")) {
		println("Flatpak metainfo already contains version $versionStr, skipping")
		return
	}

	// Format the changelog for XML
	val changelogXml = formatChangelogForXml(changelog)

	// Add new release entry after <releases> tag
	val newRelease = """        <release version="$versionStr" date="$releaseDate">
            <description>
$changelogXml
            </description>
        </release>"""

	content = content.replace(
		"<releases>",
		"<releases>\n$newRelease"
	)

	metainfoFile.writeText(content)
	println("Flatpak metainfo updated to version $versionStr")
}

/**
 * Formats a changelog text for inclusion in XML.
 * Splits the text by lines and wraps each non-empty line in <p> tags,
 * properly escaping XML special characters.
 */
private fun formatChangelogForXml(changelog: String): String {
	val lines = changelog.trim().lines()
		.map { it.trim() }
		.filter { it.isNotEmpty() }

	return if (lines.isEmpty()) {
		"                <p>Latest release</p>"
	} else {
		lines.joinToString("\n") { line ->
			val escapedLine = line
				.replace("&", "&amp;")
				.replace("<", "&lt;")
				.replace(">", "&gt;")
				.replace("\"", "&quot;")
				.replace("'", "&apos;")
			"                <p>$escapedLine</p>"
		}
	}
}
