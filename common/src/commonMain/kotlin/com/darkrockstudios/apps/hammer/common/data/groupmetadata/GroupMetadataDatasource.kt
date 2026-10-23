package com.darkrockstudios.apps.hammer.common.data.groupmetadata

import com.darkrockstudios.apps.hammer.base.GroupId
import com.darkrockstudios.apps.hammer.base.http.readTomlOrNull
import com.darkrockstudios.apps.hammer.base.http.writeToml
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import io.github.aakira.napier.Napier
import net.peanuuutz.tomlkt.Toml
import okio.FileSystem
import okio.Path
import kotlin.time.Clock

/**
 * Owns `group.toml`, the file that marks a directory as a project group. Like `project.toml` it
 * holds only regenerable metadata, so any reader may heal or recreate it: losing it costs the group
 * its identity (a fresh [GroupId]), its description and its collapsed state, never a project. A
 * blank description is stored and read as none.
 */
class GroupMetadataDatasource(
	private val fileSystem: FileSystem,
	private val toml: Toml,
) {
	fun getMetadataPath(groupDir: HPath): HPath = metadataPath(groupDir).toHPath()

	private fun metadataPath(groupDir: HPath): Path = groupDir.toOkioPath() / GroupMetadata.FILENAME

	fun isMarked(groupDir: HPath): Boolean =
		fileSystem.metadataOrNull(metadataPath(groupDir))?.isRegularFile == true

	/** A missing or corrupt file is replaced, and a missing id minted, so the result always has an id. */
	fun loadMetadata(groupDir: HPath): GroupMetadata {
		val path = metadataPath(groupDir)

		val metadata = fileSystem.readTomlOrNull<GroupMetadata>(path, toml) { e ->
			Napier.e("Failed to load group metadata: ${path.toHPath().path}", e)
		} ?: run {
			fileSystem.delete(path, false)
			return createMetadata(groupDir, GroupId.randomUUID())
		}

		if (metadata.info.groupId != null) return metadata.normalized()

		val minted = GroupId.randomUUID()
		Napier.w("Group '${groupDir.name}' had no group id, assigned ${minted.id}")
		return metadata.copy(info = metadata.info.copy(groupId = minted)).normalized().also { saveMetadata(groupDir, it) }
	}

	fun saveMetadata(groupDir: HPath, metadata: GroupMetadata) {
		fileSystem.writeToml(metadataPath(groupDir), toml, metadata.normalized(), header = FILE_HEADER)
	}

	fun updateMetadata(groupDir: HPath, block: (metadata: GroupMetadata) -> GroupMetadata): GroupMetadata {
		val newMetadata = block(loadMetadata(groupDir)).normalized()
		saveMetadata(groupDir, newMetadata)
		return newMetadata
	}

	fun createMetadata(groupDir: HPath, groupId: GroupId, description: String? = null): GroupMetadata {
		val newMetadata = GroupMetadata(
			info = GroupInfo(
				groupId = groupId,
				created = Clock.System.now(),
				description = description,
			)
		).normalized()
		saveMetadata(groupDir, newMetadata)
		return newMetadata
	}

	fun deleteMetadata(groupDir: HPath) {
		fileSystem.delete(metadataPath(groupDir), mustExist = false)
	}

	private fun GroupMetadata.normalized(): GroupMetadata =
		if (info.description?.isBlank() == true) copy(info = info.copy(description = null)) else this

	companion object {
		const val FILE_HEADER =
			"# Hammer project group metadata. Managed by Hammer: edits here may be overwritten or discarded."
	}
}
