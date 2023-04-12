package com.darkrockstudios.apps.hammer.common

interface AppCloseManager {
	suspend fun storeDirtyBuffers()
	fun hasUnsavedBuffers(): Boolean
}