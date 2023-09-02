package com.darkrockstudios.apps.hammer.common.data.projectmetadatarepository

import com.darkrockstudios.apps.hammer.common.components.projecteditor.metadata.ProjectMetadata
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import net.peanuuutz.tomlkt.Toml
import okio.FileSystem

abstract class ProjectMetadataRepository(
	protected val fileSystem: FileSystem,
	protected val toml: Toml
) {
	abstract fun loadMetadata(projectDef: ProjectDef): ProjectMetadata
	abstract fun saveMetadata(metadata: ProjectMetadata, projectDef: ProjectDef)
	abstract fun updateMetadata(projectDef: ProjectDef, block: (metadata: ProjectMetadata) -> ProjectMetadata)
	abstract fun getMetadataPath(projectDef: ProjectDef): HPath
}