package com.darkrockstudios.apps.hammer.common.data.projectmetadata

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.http.readTomlOrNull
import com.darkrockstudios.apps.hammer.base.http.writeToml
import com.darkrockstudios.apps.hammer.common.components.storyeditor.metadata.Info
import com.darkrockstudios.apps.hammer.common.components.storyeditor.metadata.ProjectMetadata
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import io.github.aakira.napier.Napier
import kotlinx.serialization.Serializable
import net.peanuuutz.tomlkt.Toml
import okio.FileSystem
import okio.Path
import kotlin.time.Clock

/**
 * Owns `project.toml`, the file that marks a directory as a project. It holds only regenerable
 * metadata, so any reader may heal or recreate it: losing it costs the project its identity (a
 * fresh [ProjectId]), never its content.
 */
class ProjectMetadataDatasource(
	private val fileSystem: FileSystem,
	private val toml: Toml
) {
	fun getMetadataPath(projectDef: ProjectDef): HPath = metadataPath(projectDef.path).toHPath()

	private fun metadataPath(projectDir: HPath): Path = projectDir.toOkioPath() / ProjectMetadata.FILENAME

	/**
	 * Does not mint: a file without a [Info.projectId] is returned without one. Use [loadProjectId]
	 * for the id.
	 */
	fun loadMetadata(projectDef: ProjectDef): ProjectMetadata = loadMetadata(projectDef.path)

	private fun loadMetadata(projectDir: HPath): ProjectMetadata {
		val path = metadataPath(projectDir)

		val metadata = fileSystem.readTomlOrNull<ProjectMetadata>(path, toml) { e ->
			Napier.e("Failed to load project metadata: ${path.toHPath().path}", e)
		} ?: run {
			// Missing or corrupt (tomlkt throws beyond SerializationException): drop the
			// bad file and start fresh so migrators re-run instead of crashing the load.
			fileSystem.delete(path, false)
			return createMetadata(projectDir, ProjectId.randomUUID())
		}

		return if (metadata.info.projectId == null) withLegacyServerId(metadata, path) else metadata
	}

	/**
	 * A file from before data version 4 holds the id the server assigned as `serverProjectId`, which
	 * the current model does not have. Reading it as the [Info.projectId] carries it through any save
	 * before Migration3_4 runs (the migrator's own version stamps included), which would otherwise
	 * drop it and leave a fresh id to be minted in its place.
	 */
	private fun withLegacyServerId(metadata: ProjectMetadata, path: Path): ProjectMetadata {
		val legacyId = fileSystem.readTomlOrNull<LegacyMetadata>(path, toml)?.info?.serverProjectId
			?: return metadata
		return metadata.copy(info = metadata.info.copy(projectId = legacyId))
	}

	fun saveMetadata(metadata: ProjectMetadata, projectDef: ProjectDef) = saveMetadata(metadata, projectDef.path)

	private fun saveMetadata(metadata: ProjectMetadata, projectDir: HPath) {
		fileSystem.writeToml(metadataPath(projectDir), toml, metadata, header = FILE_HEADER)
	}

	fun updateMetadata(
		projectDef: ProjectDef,
		block: (metadata: ProjectMetadata) -> ProjectMetadata
	) {
		val oldMetadata = loadMetadata(projectDef)
		val newMetadata = block(oldMetadata)
		saveMetadata(newMetadata, projectDef)
	}

	/**
	 * Writes a fresh file for a directory whose content is of unknown age. `dataVersion = 0` makes
	 * every migrator run over it; each one skips content that is already current.
	 */
	fun createMetadata(projectDef: ProjectDef, projectId: ProjectId): ProjectMetadata =
		createMetadata(projectDef.path, projectId)

	private fun createMetadata(projectDir: HPath, projectId: ProjectId): ProjectMetadata {
		val now = Clock.System.now()
		val newMetadata = ProjectMetadata(
			info = Info(
				created = now,
				lastAccessed = now,
				dataVersion = 0,
				projectId = projectId,
			)
		)

		saveMetadata(newMetadata, projectDir)

		return newMetadata
	}

	/** The project's id, minting and saving a new one if the file has none. */
	fun loadProjectId(projectDef: ProjectDef): ProjectId = loadProjectId(projectDef.path)

	/** [loadProjectId] for a project directory that has no [ProjectDef] yet. */
	fun loadProjectId(projectDir: HPath): ProjectId {
		val metadata = loadMetadata(projectDir)
		metadata.info.projectId?.let { return it }

		val minted = ProjectId.randomUUID()
		Napier.w("Project '${projectDir.name}' had no project id, assigned ${minted.id}")
		saveMetadata(metadata.copy(info = metadata.info.copy(projectId = minted)), projectDir)
		return minted
	}

	@Serializable
	private data class LegacyMetadata(val info: LegacyInfo = LegacyInfo())

	@Serializable
	private data class LegacyInfo(val serverProjectId: ProjectId? = null)

	companion object {
		const val FILE_HEADER =
			"# Hammer project metadata. Managed by Hammer: edits here may be overwritten or discarded."
	}
}
