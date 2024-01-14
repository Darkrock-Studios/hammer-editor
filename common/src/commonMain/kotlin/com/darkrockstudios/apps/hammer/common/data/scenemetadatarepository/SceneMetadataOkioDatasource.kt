package com.darkrockstudios.apps.hammer.common.data.scenemetadatarepository

import com.darkrockstudios.apps.hammer.base.http.readToml
import com.darkrockstudios.apps.hammer.base.http.writeToml
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.ProjectScoped
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorRepositoryOkio
import com.darkrockstudios.apps.hammer.common.data.scenemetadatarepository.SceneMetadataDatasource.Companion.DIRECTORY
import com.darkrockstudios.apps.hammer.common.dependencyinjection.DISPATCHER_DEFAULT
import com.darkrockstudios.apps.hammer.common.dependencyinjection.DISPATCHER_IO
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import net.peanuuutz.tomlkt.Toml
import okio.FileSystem
import org.koin.core.component.inject
import org.koin.core.qualifier.named
import kotlin.coroutines.CoroutineContext

class SceneMetadataOkioDatasource(
	private val fileSystem: FileSystem,
	private val toml: Toml,
	override val projectDef: ProjectDef,
) : SceneMetadataDatasource, ProjectScoped {

	override val projectScope = ProjectDefScope(projectDef)
	private val dispatcherDefault: CoroutineContext by inject(named(DISPATCHER_DEFAULT))
	private val dispatcherIo: CoroutineContext by inject(named(DISPATCHER_IO))
	private val metadataScope = CoroutineScope(dispatcherDefault)

	override suspend fun loadMetadata(sceneId: Int): SceneMetadata? = withContext(dispatcherIo) {
		val file = getMetadataPath(sceneId).toOkioPath()
		return@withContext if (fileSystem.exists(file)) {
			fileSystem.readToml(file, toml)
		} else {
			null
		}
	}

	override suspend fun storeMetadata(metadata: SceneMetadata, sceneId: Int) {
		val file = getMetadataPath(sceneId).toOkioPath()
		fileSystem.writeToml(file, toml, metadata)
		Napier.d("Metadata stored for SceneId: $sceneId")
	}

	override fun getMetadataDirectory() = getMetadataDirectory(projectDef, fileSystem)

	override fun getMetadataPath(id: Int): HPath {
		val dir = getMetadataDirectory().toOkioPath()
		val path = dir / getMetadataFilenameFromId(id)
		return path.toHPath()
	}

	override fun close() {
		metadataScope.cancel("Closing SceneMetadataOkioDatasource")
	}

	companion object {
		fun getMetadataDirectory(projectDef: ProjectDef, fileSystem: FileSystem): HPath {
			val sceneDir = SceneEditorRepositoryOkio.getSceneDirectory(projectDef, fileSystem).toOkioPath()
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