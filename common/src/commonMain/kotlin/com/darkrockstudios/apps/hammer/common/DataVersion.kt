package com.darkrockstudios.apps.hammer.common

import com.darkrockstudios.apps.hammer.base.BuildMetadata

internal var CONFIG_DATA_VERSION = 0

internal var DATA_VERSION = BuildMetadata.DATA_VERSION
fun setDataVersion(version: String) {
	DATA_VERSION = version
}

fun getDataVersion() = DATA_VERSION
