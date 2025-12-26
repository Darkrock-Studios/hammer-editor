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
 */
fun updateFlatpakFiles(newVersion: SemVar, jvmVersion: String, manifestFile: File, metainfoFile: File) {
	updateFlatpakManifest(jvmVersion, manifestFile)
	updateFlatpakMetainfo(newVersion, metainfoFile)
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

private fun updateFlatpakMetainfo(newVersion: SemVar, metainfoFile: File) {
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

	// Add new release entry after <releases> tag
	val newRelease = """        <release version="$versionStr" date="$releaseDate">
            <description>
                <p>Latest release</p>
            </description>
        </release>"""

	content = content.replace(
		"<releases>",
		"<releases>\n$newRelease"
	)

	metainfoFile.writeText(content)
	println("Flatpak metainfo updated to version $versionStr")
}
