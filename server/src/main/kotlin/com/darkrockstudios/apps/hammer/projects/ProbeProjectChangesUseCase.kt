package com.darkrockstudios.apps.hammer.projects

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.http.ProjectHashItem
import com.darkrockstudios.apps.hammer.base.http.synchronizer.ProjectContentHasher
import com.darkrockstudios.apps.hammer.project.ProjectDefinition
import com.darkrockstudios.apps.hammer.project.ProjectEntityDatasource
import com.darkrockstudios.apps.hammer.project.ProjectSyncKey
import com.darkrockstudios.apps.hammer.project.ProjectSynchronizationSession
import com.darkrockstudios.apps.hammer.project.ServerProjectDataRepository
import com.darkrockstudios.apps.hammer.syncsessionmanager.SyncSessionManager

/**
 * Pre-sync change probe: returns the subset of the requested projects whose server-side
 * project-wide content hash matches the client's. Those are guaranteed in-sync and can skip
 * their full project sync this session. Read-only: no sync session required.
 *
 * A project is omitted from the result (i.e. treated as changed) whenever the server can't be
 * certain it matches: unknown project, a missing cached entity hash, or an unreadable
 * project-data blob. Worst case is a redundant full sync, never a skipped-but-divergent project.
 */
class ProbeProjectChangesUseCase(
	private val projectsDatasource: ProjectsDatasource,
	private val projectEntityDatasource: ProjectEntityDatasource,
	private val serverProjectDataRepository: ServerProjectDataRepository,
	private val projectSyncSessionManager: SyncSessionManager<ProjectSyncKey, ProjectSynchronizationSession>,
) {
	suspend operator fun invoke(
		userId: Long,
		items: List<ProjectHashItem>,
	): Set<ProjectId> {
		val unchanged = mutableSetOf<ProjectId>()
		for (item in items) {
			val projectDef = projectsDatasource.getProject(userId, item.projectId) ?: continue
			// A project with an in-flight sync (e.g. from another device) may have half-updated
			// stored hashes, so we can't certify it "unchanged". Omit it → the client full-syncs.
			if (projectSyncSessionManager.hasActiveSyncSession(ProjectSyncKey(userId, projectDef))) continue
			val serverHash = computeProjectContentHash(userId, projectDef) ?: continue
			if (serverHash == item.hash) {
				unchanged += item.projectId
			}
		}
		return unchanged
	}

	/**
	 * Recomputes a project's project-wide content hash from the server's stored state (cached entity
	 * hashes + the project-data blob hash), using the same [ProjectContentHasher] the client runs.
	 * Returns null when the hash can't be computed reliably, so the caller treats the project as changed.
	 */
	private suspend fun computeProjectContentHash(
		userId: Long,
		projectDef: ProjectDefinition,
	): String? {
		// One query for all (id, hash) pairs rather than a per-entity lookup.
		val entityHashes = projectEntityDatasource.getEntityHashes(userId, projectDef)

		val projectDataHash = serverProjectDataRepository.loadVerifiedDataHash(userId, projectDef) ?: return null

		return ProjectContentHasher.hash(entityHashes, projectDataHash)
	}
}
