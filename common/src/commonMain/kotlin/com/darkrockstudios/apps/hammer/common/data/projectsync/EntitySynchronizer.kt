package com.darkrockstudios.apps.hammer.common.data.projectsync

interface EntitySynchronizer {
	suspend fun uploadScene(id: Int, syncId: String)
	suspend fun downloadEntity(id: Int, syncId: String)
	suspend fun reIdEntity(oldId: Int, newId: Int)
	suspend fun finalizeSync()
}