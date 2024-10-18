package com.darkrockstudios.apps.hammer.common.data.projectmetadata

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.common.components.storyeditor.metadata.Info
import com.darkrockstudios.apps.hammer.common.components.storyeditor.metadata.ProjectMetadata
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import io.github.aakira.napier.Napier
import kotlinx.datetime.Clock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import net.peanuuutz.tomlkt.Toml
import okio.FileSystem
import okio.IOException

class ProjectMetadataDatasource(
	private val fileSystem: FileSystem,
	private val toml: Toml
) {
	fun getMetadataPath(projectDef: ProjectDef): HPath {
		return (projectDef.path.toOkioPath() / ProjectMetadata.FILENAME).toHPath()
	}

	fun loadMetadata(projectDef: ProjectDef): ProjectMetadata {
		val path = getMetadataPath(projectDef).toOkioPath()

		val metadata = try {
			val metadataText = fileSystem.read(path) {
				readUtf8()
			}
			toml.decodeFromString(metadataText)
		} catch (e: IOException) {
			Napier.e("Failed to load project metadata: ${path.toHPath().path}")

			// Delete any old corrupt file if we got here
			fileSystem.delete(path, false)

			createNewMetadata(projectDef)
		}

		return metadata
	}

	fun saveMetadata(metadata: ProjectMetadata, projectDef: ProjectDef) {

		val path = getMetadataPath(projectDef).toOkioPath()

		val metadataText = toml.encodeToString<ProjectMetadata>(metadata)

		fileSystem.write(path, false) {
			writeUtf8(metadataText)
		}
	}

	fun updateMetadata(
		projectDef: ProjectDef,
		block: (metadata: ProjectMetadata) -> ProjectMetadata
	) {
		val oldMetadata = loadMetadata(projectDef)
		val newMetadata = block(oldMetadata)
		saveMetadata(newMetadata, projectDef)
	}

	private fun createNewMetadata(projectDef: ProjectDef): ProjectMetadata {
		val newMetadata = ProjectMetadata(
			info = Info(
				created = Clock.System.now(),
				lastAccessed = Clock.System.now(),
				// We don't know the version, start at 0 so all migrators will run
				dataVersion = 0
			)
		)

		saveMetadata(newMetadata, projectDef)

		return newMetadata
	}
}

fun ProjectMetadataDatasource.loadProjectId(projectDef: ProjectDef): ProjectId? {
	return loadMetadata(projectDef).info.serverProjectId
}

fun ProjectMetadataDatasource.requireProjectId(projectDef: ProjectDef): ProjectId {
	return loadProjectId(projectDef) ?: error("Project has no server project id")
}