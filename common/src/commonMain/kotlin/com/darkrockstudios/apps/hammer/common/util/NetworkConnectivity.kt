package com.darkrockstudios.apps.hammer.common.util

expect class NetworkConnectivity {
	suspend fun hasActiveConnection(): Boolean
}