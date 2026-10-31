package com.darkrockstudios.apps.hammer.common.data.duplicateids

import com.darkrockstudios.apps.hammer.common.fileio.HPath

enum class DuplicateIdKind {
	Entry,
	Scene,
	Draft,
	Note,
	Image,
}

/** One file (or scene group directory) claiming an id. */
data class DuplicateCopy(
	val path: HPath,
	/** [path] relative to the project directory. */
	val relativePath: String,
	val name: String,
	val isGroup: Boolean,
	val archived: Boolean,
	val sizeBytes: Long?,
	val modifiedAtMillis: Long?,
	/** Readable content: entry text, scene or draft markdown, note body. Empty for groups and images. */
	val text: String,
	/** Null for groups. */
	val contentHash: String?,
)

/**
 * Files that all claim [id] within one id space. [copies] are in the order the read path picks
 * them, so the first is the one the app currently uses.
 */
data class DuplicateIdSet(
	val kind: DuplicateIdKind,
	val id: Int,
	val copies: List<DuplicateCopy>,
) {
	val inUse: DuplicateCopy get() = copies.first()
}

class StaleDuplicateIdSet(set: DuplicateIdSet) :
	IllegalStateException("Files for ${set.kind} ${set.id} changed on disk since they were scanned")
