package com.darkrockstudios.apps.hammer.common.util

import com.darkrockstudios.apps.hammer.base.BuildMetadata
import com.darkrockstudios.apps.hammer.common.getInDevelopmentMode

fun getAppVersionString(): String {
	return if (getInDevelopmentMode()) {
		"v${BuildMetadata.APP_VERSION}-dev"
	} else {
		"v${BuildMetadata.APP_VERSION}"
	}
}

fun isNewVersionAvailable(latestVersion: String): Boolean {
	val curVersion = getAppVersionString()
	return stripReleaseSuffix(latestVersion) != stripReleaseSuffix(curVersion)
		&& curVersion.endsWith("-dev").not()
}

/**
 * Strips a `+platform+platform` partial-release suffix from a tag string,
 * leaving the bare `vX.Y.Z` (or `vX.Y.Z-dev`). No-op if no `+` is present.
 */
fun stripReleaseSuffix(version: String): String = version.substringBefore('+')