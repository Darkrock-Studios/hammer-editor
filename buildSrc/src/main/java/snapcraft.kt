package com.darkrockstudios.build

import java.io.File

/**
 * Updates the snapcraft.yaml file with the new version and JVM version.
 *
 * @param newVersion The new app version (e.g., "1.12.1")
 * @param jvmVersion The JVM version to use (e.g., "21")
 * @param snapcraftFile The snapcraft.yaml file to update
 */
fun updateSnapcraftYaml(newVersion: SemVar, jvmVersion: String, snapcraftFile: File) {
	if (!snapcraftFile.exists()) {
		println("Warning: snapcraft.yaml not found at ${snapcraftFile.absolutePath}, skipping update")
		return
	}

	var content = snapcraftFile.readText()

	// Update version line (e.g., version: '1.12.1')
	content = content.replace(
		Regex("""version:\s*['"]?\d+\.\d+\.\d+['"]?"""),
		"version: '$newVersion'"
	)

	// Update openjdk-XX-jdk references
	content = content.replace(
		Regex("""openjdk-\d+-jdk"""),
		"openjdk-$jvmVersion-jdk"
	)

	// Update openjdk-XX-jre references
	content = content.replace(
		Regex("""openjdk-\d+-jre"""),
		"openjdk-$jvmVersion-jre"
	)

	// Update java-XX-openjdk references (for JAVA_HOME path)
	content = content.replace(
		Regex("""java-\d+-openjdk"""),
		"java-$jvmVersion-openjdk"
	)

	snapcraftFile.writeText(content)
	println("snapcraft.yaml updated to version $newVersion with JVM $jvmVersion")
}
