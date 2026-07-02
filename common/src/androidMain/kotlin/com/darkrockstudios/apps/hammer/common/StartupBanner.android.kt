package com.darkrockstudios.apps.hammer.common

import android.os.Build

actual fun platformStartupInfo(): String {
	return "OS: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})" +
		" | device: ${Build.MANUFACTURER} ${Build.MODEL}" +
		" | abi: ${Build.SUPPORTED_ABIS.firstOrNull() ?: "n/a"}"
}
