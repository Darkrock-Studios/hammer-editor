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
	return latestVersion != curVersion && curVersion.endsWith("-dev").not()
}