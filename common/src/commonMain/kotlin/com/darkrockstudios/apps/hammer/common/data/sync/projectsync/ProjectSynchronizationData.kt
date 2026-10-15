package com.darkrockstudios.apps.hammer.common.data.sync.projectsync

import com.darkrockstudios.apps.hammer.base.ProjectId
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class RenamedProject(
	val projectId: ProjectId,
	val newName: String,
)

@Serializable
data class ProjectsSynchronizationData(
	val deletedProjects: Set<ProjectId>,
	val projectsToDelete: Set<ProjectId>,
	val projectsToRename: Set<RenamedProject>,
)

@Serializable
data class ProjectSynchronizationData(
	val currentSyncId: String? = null,
	val lastId: Int,
	val newIds: List<Int>,
	val lastSync: Instant,
	val dirty: List<EntityOriginalState>,
	val deletedIds: Set<Int>,
	// The hash the server last confirmed it holds, per entity: the locked conflict baseline. Set
	// only on a successful transfer (to the hash of exactly what was sent), never re-derived from
	// local state — so a field that mutates after a sync (e.g. `lastEdited`) can't taint it.
	val syncedHashes: Map<Int, String> = emptyMap(),
	/**
	 * The project-wide content hash as of the last successful sync, read by the pre-sync change
	 * probe to skip unchanged projects. Written only by FinalizeSyncOperation; cleared by any
	 * content mutation (see SyncJournal). Null/absent means "recompute via a normal sync".
	 */
	val cachedProjectHash: String? = null,
	/** [ProjectContentHasher.ALGO_VERSION] under which [cachedProjectHash] was computed. */
	val hashAlgoVersion: Int = 0,
	/**
	 * Set when the project was restored from a backup. The journal came with the backup, so it
	 * describes the server as it was then, and an entity it lists as clean would silently take the
	 * server's newer copy. The next full sync marks every local entity dirty against its best known
	 * baseline instead, so each one is conflict-checked against the server.
	 */
	val verifyAllEntities: Boolean = false,
)

@Serializable
data class EntityOriginalState(
	val id: Int,
	// Null when the server has never confirmed a hash for this entity (brand-new, or first edit
	// after upgrading to synced-hash tracking). A null baseline tells the server to skip the
	// conflict check and accept the upload (the entity self-heals its baseline on that sync).
	val originalHash: String? = null,
)