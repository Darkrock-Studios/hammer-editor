package com.darkrockstudios.apps.hammer.common.data.sync.accountsync

import com.darkrockstudios.apps.hammer.base.GroupId
import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.GroupBaseline
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.ProjectBaseline
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.ProjectsSynchronizationData

/**
 * The baselines one account sync session settles, merged into the stored ones once at its end. An
 * item that failed or was skipped is never recorded, so it keeps its old baseline and is retried.
 */
class BaselineLedger {
	private val groups = mutableMapOf<GroupId, GroupBaseline>()
	private val projects = mutableMapOf<ProjectId, ProjectBaseline>()
	private val retiredGroups = mutableSetOf<GroupId>()
	private val retiredProjects = mutableSetOf<ProjectId>()
	private val seededProjects = mutableMapOf<ProjectId, ProjectBaseline>()

	/** Groups the server holds this session: a placement or parent may only name one of these. */
	val confirmedGroupIds = mutableSetOf<GroupId>()

	val recordedGroups: Map<GroupId, GroupBaseline> get() = groups
	val recordedProjects: Map<ProjectId, ProjectBaseline> get() = projects

	fun recordGroup(id: GroupId, baseline: GroupBaseline) {
		groups[id] = baseline
		retiredGroups -= id
	}

	fun retireGroup(id: GroupId) {
		groups -= id
		retiredGroups += id
	}

	fun recordProject(id: ProjectId, baseline: ProjectBaseline) {
		projects[id] = baseline
		retiredProjects -= id
	}

	fun retireProject(id: ProjectId) {
		projects -= id
		retiredProjects += id
	}

	/** Baselines stored even if the project does not settle this session, see [seedFromLegacyRenames]. */
	fun seedProjects(seeds: Map<ProjectId, ProjectBaseline>) {
		seededProjects += seeds
	}

	/** A local group took the id [new], adopted from the server or re-minted. */
	fun rekeyGroup(old: GroupId, new: GroupId) {
		groups.remove(old)?.let { groups[new] = it }
		retiredGroups += old
		retiredGroups -= new
		if (confirmedGroupIds.remove(old)) confirmedGroupIds += new
		groups.entries.forEach { entry ->
			if (entry.value.parentId == old) entry.setValue(entry.value.copy(parentId = new))
		}
		projects.entries.forEach { entry ->
			if (entry.value.groupId == old) entry.setValue(entry.value.copy(groupId = new))
		}
	}

	fun applyTo(data: ProjectsSynchronizationData): ProjectsSynchronizationData = data.copy(
		groupBaselines = data.groupBaselines - retiredGroups + groups,
		projectBaselines = data.projectBaselines + seededProjects - retiredProjects + projects,
	)
}
