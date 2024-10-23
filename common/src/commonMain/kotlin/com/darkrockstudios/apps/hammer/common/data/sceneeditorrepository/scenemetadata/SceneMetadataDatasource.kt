package com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.scenemetadata

import com.darkrockstudios.apps.hammer.base.http.readToml
import com.darkrockstudios.apps.hammer.base.http.writeToml
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.ProjectScoped
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneDatasource
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import com.darkrockstudios.apps.hammer.common.dependencyinjection.injectIoDispatcher
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import io.github.aakira.napier.Napier
import kotlinx.coroutines.withContext
import net.peanuuutz.tomlkt.Toml
import okio.FileSystem

class SceneMetadataDatasource(
	private val fileSystem: FileSystem,
	private val toml: Toml,
	val projectDef: ProjectDef,
) : ProjectScoped {

	override val projectScope = ProjectDefScope(projectDef)
	private val dispatcherIo by injectIoDispatcher()

	suspend fun loadMetadata(sceneId: Int): SceneMetadata? = withContext(dispatcherIo) {
		val file = getMetadataPath(sceneId).toOkioPath()
		return@withContext if (fileSystem.exists(file)) {
			fileSystem.readToml(file, toml)
		} else {
			null
		}
	}

	suspend fun storeMetadata(metadata: SceneMetadata, sceneId: Int) {
		val file = getMetadataPath(sceneId).toOkioPath()
		fileSystem.writeToml(file, toml, metadata)
		Napier.d("Metadata stored for SceneId: $sceneId")
	}

	private fun getMetadataDirectory() = Companion.getMetadataDirectory(projectDef, fileSystem)

	fun getMetadataPath(id: Int): HPath {
		val dir = getMetadataDirectory().toOkioPath()
		val path = dir / getMetadataFilenameFromId(id)
		return path.toHPath()
	}

	fun reIdSceneMetadata(oldId: Int, newId: Int) {
		val oldPath = getMetadataPath(oldId).toOkioPath()
		val newPath = getMetadataPath(newId).toOkioPath()

		if (fileSystem.exists(oldPath)) {
			Napier.i("Re-ID of Scene Metadata. Old ID: $oldId New ID: $newId")
			fileSystem.atomicMove(oldPath, newPath)
		}
	}

	companion object {
		private const val DIRECTORY = ".metadata"

		fun getMetadataDirectory(projectDef: ProjectDef, fileSystem: FileSystem): HPath {
			val sceneDir = SceneDatasource.getSceneDirectory(projectDef, fileSystem).toOkioPath()
			val metadataDirPath = sceneDir.div(DIRECTORY)
			if (!fileSystem.exists(metadataDirPath)) {
				fileSystem.createDirectories(metadataDirPath)
			}
			return metadataDirPath.toHPath()
		}

		fun getMetadataFilenameFromId(id: Int): String {
			return "scene-$id-metadata.toml"
		}
	}
}