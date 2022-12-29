package com.darkrockstudios.apps.hammer.common

interface AppCloseManager {
    fun storeDirtyBuffers()
    fun hasUnsavedBuffers(): Boolean
}