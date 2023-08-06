package com.darkrockstudios.apps.hammer.common

actual fun getInDevelopmentMode(): Boolean {
	return Platform.isDebugBinary
}
