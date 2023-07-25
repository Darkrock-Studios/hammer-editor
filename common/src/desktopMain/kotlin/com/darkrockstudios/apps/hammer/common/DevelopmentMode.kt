package com.darkrockstudios.apps.hammer.common

internal var isDevelopmentMode = false
fun setInDevelopmentMode(isDevelopment: Boolean) {
	isDevelopmentMode = isDevelopment
}

actual fun getInDevelopmentMode() = isDevelopmentMode
