package com.darkrockstudios.apps.hammer.base

/**
 * The installer format this build was packaged as, baked in at build time from `-Pformat=<token>`.
 * The second axis next to [DistributionChannel]: the channel says which vehicle the build is for
 * and whether it may update itself, the format says which installer artifact it came from. Dev
 * runs and anything built without a packaging task report [NONE].
 *
 * Kept in step with the build-side enum in `buildSrc/src/main/java/packageFormat.kt`.
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
		/** An unrecognised token falls back to [NONE] for the same reason [DistributionChannel.current] falls back to DEV. */
		val current: PackageFormat =
			entries.firstOrNull { it.token == BuildMetadata.PACKAGE_FORMAT } ?: NONE
	}
}
