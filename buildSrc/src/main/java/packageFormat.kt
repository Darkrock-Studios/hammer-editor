package com.darkrockstudios.build

import org.gradle.api.Project

/**
 * Build-side mirror of the runtime `PackageFormat` enum in `:base`. The two lists have to stay
 * in step; `PackageFormatTest` in `:base` pins the token strings so a change to one without the
 * other fails a test rather than shipping a build that reports the wrong format.
 */
enum class PackageFormat(val token: String) {
	NONE("none"),
	MSI("msi"),
	EXE("exe"),
	DEB("deb"),
	RPM("rpm"),
	DMG("dmg"),
	PKG("pkg"),
	APPIMAGE("appimage"),
	MSIX("msix"),
	SNAP("snap"),
	FLATPAK("flatpak"),
	;

	companion object {
		fun fromToken(token: String): PackageFormat? = entries.firstOrNull { it.token == token }
	}
}

/**
 * Resolves the installer format for this build from `-Pformat=<token>`, defaulting to [NONE]
 * for anything that is not a packaging run. Throws on an unknown token, so a typo in a release
 * workflow fails the build instead of quietly shipping an installer that cannot name itself.
 */
fun resolvePackageFormat(formatProperty: String?): PackageFormat {
	val requested = formatProperty?.trim()?.takeIf { it.isNotEmpty() } ?: return PackageFormat.NONE

	return PackageFormat.fromToken(requested)
		?: error(
			"Unknown -Pformat=$requested. Valid formats: " +
				PackageFormat.entries.joinToString(", ") { it.token }
		)
}

/** The installer format this build is packaged as, from `-Pformat`. */
fun Project.packageFormat(): PackageFormat = resolvePackageFormat(findProperty("format")?.toString())
