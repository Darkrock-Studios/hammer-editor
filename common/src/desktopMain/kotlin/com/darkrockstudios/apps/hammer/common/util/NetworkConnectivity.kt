package com.darkrockstudios.apps.hammer.common.util

import java.io.IOException
import java.net.InetAddress

actual class NetworkConnectivity {
	actual suspend fun hasActiveConnection(): Boolean {
		return try {
			val addr = InetAddress.getByName("google.com")
			addr != null
		} catch (_: IOException) {
			false
		}
	}
}