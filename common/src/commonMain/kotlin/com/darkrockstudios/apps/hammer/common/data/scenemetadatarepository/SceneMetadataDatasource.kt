package com.darkrockstudios.apps.hammer.common.data.scenemetadatarepository

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import okio.Closeable

interface SceneMetadataDatasource : Closeable {
	val projectDef: ProjectDef

	suspend fun loadMetadata(sceneId: Int): SceneMetadata?
	suspend fun storeMetadata(metadata: SceneMetadata, sceneId: Int)

	fun getMetadataDirectory(): HPath
	fun getMetadataPath(id: Int): HPath

	companion object {
		const val DIRECTORY = ".metadata"
	}
}