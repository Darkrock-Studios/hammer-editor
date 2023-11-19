package com.darkrockstudios.apps.hammer.common

import kotlin.experimental.ExperimentalNativeApi

@OptIn(ExperimentalNativeApi::class)
actual fun getInDevelopmentMode(): Boolean {
	return Platform.isDebugBinary
}
