package com.darkrockstudios.apps.hammer.common.data.sync.accountsync

import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.base.GroupId
import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.http.ApiGroupDefinition
import com.darkrockstudios.apps.hammer.base.http.ApiProjectDefinition
import com.darkrockstudios.apps.hammer.common.data.CResult
import com.darkrockstudios.apps.hammer.common.data.ClientResult
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.isSuccess
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.GroupDef
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectRenameFailed
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.OnSyncLog
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.ProjectBaseline
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.syncAccLogE
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.syncAccLogI
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.syncAccLogW
import com.darkrockstudios.apps.hammer.common.server.ServerProjectsApi
import com.darkrockstudios.apps.hammer.common.util.StrRes
import io.github.aakira.napier.Napier

/**
 * The names and placements of the projects both sides hold, reconciled by the three-way rule of
 * [reconcileProjectAttributes]. Every rename runs before any move. A project's baseline is recorded
 * in the [BaselineLedger] only once every action it needed succeeded.
 */
class ProjectAttributeSyncPhase(
	private val projectsRepository: ProjectsRepository,
	private val serverProjectsApi: ServerProjectsApi,
	private val strRes: StrRes,
) {
	suspend fun reconcile(
		syncId: String,
		serverProjects: Collection<ApiProjectDefinition>,
		serverGroups: Collection<ApiGroupDefinition>,
		baselines: Map<ProjectId, ProjectBaseline>,
		excluded: Set<ProjectId>,
		ledger: BaselineLedger,
		onLog: OnSyncLog,
		onUnauthorized: suspend () -> Unit,
	) {
		val tree = projectsRepository.getProjectTree()
		val plans = reconcileProjectAttributes(
			local = tree.projects,
			placement = tree.placement,
			server = serverProjects,
			baselines = baselines,
			excluded = excluded,
			confirmedGroupIds = ledger.confirmedGroupIds,
		)

		val current = mutableMapOf<ProjectId, ProjectDef>()
		val failed = mutableSetOf<ProjectId>()

		plans.forEach { plan ->
			val id = plan.local.id
			plan.actions.forEach { action ->
				val ok = when (action) {
					is ProjectAttributeAction.RenameOnServer -> renameOnServer(id, action.name, syncId, onLog, onUnauthorized)
					is ProjectAttributeAction.RenameLocally -> renameLocally(plan.local, action.name, onLog)
						?.also { current[id] = it } != null

					else -> true
				}
				if (!ok) failed += id
			}
		}

		val serverGroupsById = serverGroups.associateBy { it.uuid }
		plans.forEach { plan ->
			val id = plan.local.id
			val def = current[id] ?: plan.local
			plan.actions.forEach { action ->
				val ok = when (action) {
					is ProjectAttributeAction.MoveOnServer -> moveOnServer(def, action.groupId, syncId, onLog, onUnauthorized)
					is ProjectAttributeAction.MoveLocally -> {
						val target = tree.nearestLocalGroup(action.groupId, serverGroupsById)
						val moved = target?.id == tree.placement[id] || moveLocally(def, target, onLog)
						if (target?.id != action.groupId) {
							onLog(syncAccLogW(strRes.get(Res.string.sync_log_account_project_move_client_group_missing, def.name)))
						}
						moved && target?.id == action.groupId
					}

					else -> true
				}
				if (!ok) failed += id
			}
		}

		plans
			.filter { it.local.id !in failed }
			.forEach { plan -> plan.baseline?.let { ledger.recordProject(plan.local.id, it) } }
	}

	private suspend fun renameOnServer(
		id: ProjectId,
		name: String,
		syncId: String,
		onLog: OnSyncLog,
		onUnauthorized: suspend () -> Unit,
	): Boolean {
		val result = serverProjectsApi.renameProject(id, syncId, name)
		val error = result.exceptionOrNull() ?: run {
			onLog(syncAccLogI(strRes.get(Res.string.sync_log_account_project_rename_server_success, name)))
			return true
		}
		Napier.e("Failed to rename project on server: ${id.id}", error)
		onLog(syncAccLogE(strRes.get(Res.string.sync_log_account_project_rename_server_failure, name)))
		if (error.isAuthenticationFailure()) onUnauthorized()
		return false
	}

	/** The renamed project, or null when it could not be renamed. */
	private suspend fun renameLocally(def: ProjectDef, name: String, onLog: OnSyncLog): ProjectDef? {
		val result = projectsRepository.renameProject(def, name)
		if (isSuccess(result)) {
			onLog(syncAccLogI(strRes.get(Res.string.sync_log_account_project_rename_client_from_server_success, name)))
			return result.data
		}
		if (result.isInUse()) {
			logInUse(def, onLog)
		} else {
			onLog(syncAccLogE(strRes.get(Res.string.sync_log_account_project_rename_client_from_server_failure, def.name)))
		}
		return null
	}

	private suspend fun moveOnServer(
		def: ProjectDef,
		groupId: GroupId?,
		syncId: String,
		onLog: OnSyncLog,
		onUnauthorized: suspend () -> Unit,
	): Boolean {
		val result = serverProjectsApi.moveProject(def.id, groupId, syncId)
		val error = result.exceptionOrNull() ?: run {
			onLog(syncAccLogI(strRes.get(Res.string.sync_log_account_project_move_server_success, def.name)))
			return true
		}
		Napier.e("Failed to move project on server: ${def.name}", error)
		onLog(syncAccLogE(strRes.get(Res.string.sync_log_account_project_move_server_failure, def.name)))
		if (error.isAuthenticationFailure()) onUnauthorized()
		return false
	}

	private suspend fun moveLocally(
		def: ProjectDef,
		target: GroupDef?,
		onLog: OnSyncLog,
	): Boolean {
		val result = projectsRepository.moveProject(def, target)
		if (isSuccess(result)) {
			onLog(syncAccLogI(strRes.get(Res.string.sync_log_account_project_move_client_from_server_success, def.name)))
			return true
		}
		if (result.isInUse()) {
			logInUse(def, onLog)
		} else {
			onLog(syncAccLogE(strRes.get(Res.string.sync_log_account_project_move_client_from_server_failure, def.name)))
		}
		return false
	}

	private suspend fun logInUse(def: ProjectDef, onLog: OnSyncLog) {
		onLog(syncAccLogW(strRes.get(Res.string.sync_log_account_project_in_use, def.name)))
	}
}

internal fun CResult<*>.isInUse(): Boolean =
	((this as? ClientResult.Failure<*>)?.exception as? ProjectRenameFailed)?.reason ==
		ProjectRenameFailed.Reason.ProjectInUse
