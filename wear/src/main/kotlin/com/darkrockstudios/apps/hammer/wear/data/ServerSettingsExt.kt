package com.darkrockstudios.apps.hammer.wear.data

import com.darkrockstudios.apps.hammer.common.data.globalsettings.ServerSettings

/** Account setup stores provisional settings with no user before sign-in completes. */
fun ServerSettings?.isSignedIn(): Boolean =
	this != null && userId >= 0 && !bearerToken.isNullOrBlank()
