package com.darkrockstudios.build

import java.io.File

/**
 * Updates CFBundleShortVersionString (the iOS marketing version) in Info.plist.
 *
 * macOS gets this for free via Compose's `packageVersion`; iOS has no such hook,
 * so prepareForRelease writes it here to keep the plist in sync with the semver.
 * CFBundleVersion (the build number) is left alone — Fastlane sets that at build time.
 *
 * @param newVersion The new app version (e.g., "1.12.1")
 * @param infoPlistFile The Info.plist file to update
 */
fun updateIosShortVersion(newVersion: SemVar, infoPlistFile: File) {
	if (!infoPlistFile.exists()) {
		println("Warning: Info.plist not found at ${infoPlistFile.absolutePath}, skipping update")
		return
	}

	val content = infoPlistFile.readText()
	// Match the <string> paired with the CFBundleShortVersionString key, preserving
	// the surrounding whitespace so we don't touch CFBundleVersion or anything else.
	val regex = Regex("""(<key>CFBundleShortVersionString</key>\s*<string>)[^<]*(</string>)""")
	if (!regex.containsMatchIn(content)) {
		error("CFBundleShortVersionString not found in ${infoPlistFile.absolutePath}")
	}

	val updated = regex.replace(content) { m -> "${m.groupValues[1]}$newVersion${m.groupValues[2]}" }
	infoPlistFile.writeText(updated)
	println("Info.plist CFBundleShortVersionString updated to $newVersion")
}
