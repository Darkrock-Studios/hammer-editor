package com.darkrockstudios.apps.hammer.common.util

actual class NetworkConnectivity {
	actual suspend fun hasActiveConnection(): Boolean {
		// TODO implement for iOS
		return true
	}
}