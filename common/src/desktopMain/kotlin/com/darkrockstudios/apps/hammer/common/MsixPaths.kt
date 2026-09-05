package com.darkrockstudios.apps.hammer.common

import com.darkrockstudios.apps.hammer.base.DistributionChannel

/**
 * Package family name of the Microsoft Store build: `Identity/@Name` from `msix/AppxManifest.xml`
 * plus the hash Windows derives from the publisher ID. The hash is not in the manifest and cannot
 * be computed from it, so it is hardcoded; it only changes if the Store identity does.
 */
private const val PACKAGE_FAMILY_NAME = "DarkRockStudios.HammerEditor_eee5gzg80tyea"

private const val PACKAGES = "Packages"
private const val LOCAL_CACHE = "LocalCache\\Local"

/**
 * Rewrites a path so the Windows shell can find it.
 *
 * MSIX filesystem redirection is asymmetric: the app's writes under `%LOCALAPPDATA%` land inside
 * the package container, but reads fall through, so `File.exists()` is true from inside the
 * container and the app is satisfied. Explorer runs outside the container and correctly reports
 * nothing at the literal path, which is what the "location is not available" dialog on the
 * open-logs button was.
 *
 * A no-op on every other channel and platform, and on a path that is already un-redirected.
 */
fun shellPath(path: String): String =
	if (DistributionChannel.current == DistributionChannel.MICROSOFT_STORE && hostOs == HostOs.Windows) {
		unredirectMsixPath(path, System.getenv("LOCALAPPDATA"))
	} else {
		path
	}

/** The rewrite itself, with `%LOCALAPPDATA%` passed in so it can be tested off Windows. */
internal fun unredirectMsixPath(path: String, localAppData: String?): String {
	if (localAppData.isNullOrBlank()) return path

	val base = localAppData.trimEnd('\\')
	val prefix = "$base\\"
	if (!path.startsWith(prefix, ignoreCase = true)) return path

	val relative = path.substring(prefix.length)
	if (relative.startsWith("$PACKAGES\\", ignoreCase = true)) return path

	return "$base\\$PACKAGES\\$PACKAGE_FAMILY_NAME\\$LOCAL_CACHE\\$relative"
}
