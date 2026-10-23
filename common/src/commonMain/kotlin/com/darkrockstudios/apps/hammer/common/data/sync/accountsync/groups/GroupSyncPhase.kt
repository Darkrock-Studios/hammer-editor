package com.darkrockstudios.apps.hammer.common.data.sync.accountsync.groups

import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.base.GroupId
import com.darkrockstudios.apps.hammer.base.http.ApiErrorCode
import com.darkrockstudios.apps.hammer.base.http.ApiGroupDefinition
import com.darkrockstudios.apps.hammer.base.http.MAX_GROUP_NESTING_DEPTH
import com.darkrockstudios.apps.hammer.common.data.CResult
import com.darkrockstudios.apps.hammer.common.data.ClientResult
import com.darkrockstudios.apps.hammer.common.data.isSuccess
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.GroupDef
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.GroupOperationFailed
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectTree
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.BaselineLedger
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.Winner
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.isAuthenticationFailure
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.GroupBaseline
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.OnSyncLog
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.apiErrorCode
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncLogLevel
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.syncAccLog
import com.darkrockstudios.apps.hammer.common.server.ServerProjectsApi
import com.darkrockstudios.apps.hammer.common.util.StrRes
import io.github.aakira.napier.Napier
import okio.IOException
import org.jetbrains.compose.resources.StringResource

/** Group deletions held back until the projects have moved out of the groups. */
data class PendingGroupDeletes(
	val onServer: List<ApiGroupDefinition>,
	/** Children first. */
	val locally: List<GroupId>,
)

/**
 * The groups half of account sync: brings the local and server group trees together by the three-way
 * rule of [reconcileGroups], recording a group's baseline in the [BaselineLedger] only once every
 * action it needed succeeded. Deletions run last, from [applyDeletions], once the projects have moved.
 */
class GroupSyncPhase(
	private val projectsRepository: ProjectsRepository,
	private val serverProjectsApi: ServerProjectsApi,
	private val strRes: StrRes,
) {
	private class Session(
		val syncId: String,
		val ledger: BaselineLedger,
		val onLog: OnSyncLog,
		val onUnauthorized: suspend () -> Unit,
		val localIds: MutableSet<GroupId>,
	) {
		/** Local ids replaced this session, by adoption or re-mint. */
		val remap = mutableMapOf<GroupId, GroupId>()

		/** Server groups now bound to a local group they were not paired with at the start. */
		val claimed = mutableSetOf<GroupId>()

		/** Local groups that did not reach the server; their subgroups are skipped. */
		val failed = mutableSetOf<GroupId>()

		fun resolve(id: GroupId?): GroupId? = id?.let { remap[it] ?: it }
	}

	suspend fun reconcile(
		syncId: String,
		serverGroups: Set<ApiGroupDefinition>,
		baselines: Map<GroupId, GroupBaseline>,
		ledger: BaselineLedger,
		onLog: OnSyncLog,
		onUnauthorized: suspend () -> Unit,
	): PendingGroupDeletes {
		ledger.confirmedGroupIds += serverGroups.map { it.uuid }

		val (plan, tree) = settleAdoptions(serverGroups, baselines, ledger, onLog)
		val session = Session(
			syncId = syncId,
			ledger = ledger,
			onLog = onLog,
			onUnauthorized = onUnauthorized,
			localIds = tree.groups.mapTo(mutableSetOf()) { it.id },
		)

		createOnServer(plan.createOnServer, session)
		createLocally(plan.createLocally.filter { it.uuid !in session.claimed }, session)
		applyUpdates(plan.updates, session)
		plan.agreed.forEach { (id, baseline) -> ledger.recordGroup(id, baseline) }

		return PendingGroupDeletes(
			onServer = plan.deleteOnServer.filter { it.uuid !in session.claimed },
			locally = plan.deleteLocally.map { it.id },
		)
	}

	/**
	 * Adopts server groups by name until none is left to adopt: a subgroup can only match once its
	 * parent has taken the server's id, so each pass reaches one level deeper. Returns the final plan
	 * with the tree it was made from.
	 */
	private suspend fun settleAdoptions(
		serverGroups: Set<ApiGroupDefinition>,
		baselines: Map<GroupId, GroupBaseline>,
		ledger: BaselineLedger,
		onLog: OnSyncLog,
	): Pair<GroupPlan, ProjectTree> {
		val failedAdoptions = mutableSetOf<GroupId>()
		var passes = 0
		while (true) {
			val tree = projectsRepository.getProjectTree()
			val plan = reconcileGroups(tree.groups, serverGroups, baselines, failedAdoptions)
			if (plan.adoptions.isEmpty() || passes++ > MAX_GROUP_NESTING_DEPTH) return plan to tree

			plan.adoptions.forEach { adoption ->
				try {
					projectsRepository.setGroupId(adoption.local, adoption.serverId)
					ledger.rekeyGroup(adoption.local.id, adoption.serverId)
					onLog.log(SyncLogLevel.INFO, Res.string.sync_log_account_group_adopt_server, adoption.local.name)
				} catch (e: IOException) {
					Napier.e("Failed to adopt server group for '${adoption.local.name}'", e)
					failedAdoptions += adoption.local.id
				}
			}
		}
	}

	private suspend fun createOnServer(groups: List<GroupDef>, session: Session) {
		groups.forEach { group ->
			val parentId = session.resolve(group.parentId)
			if (parentId != null && (parentId in session.failed || parentId !in session.ledger.confirmedGroupIds)) {
				session.failed += group.id
				session.onLog.log(SyncLogLevel.WARN, Res.string.sync_log_account_group_skipped_parent_failed, group.name)
				return@forEach
			}
			createOnServer(group, parentId, session)
		}
	}

	private suspend fun createOnServer(group: GroupDef, parentId: GroupId?, session: Session) {
		val description = group.description.orNullIfBlank()
		var def = group
		var reminted = false
		while (true) {
			val request = ApiGroupDefinition(def.id, def.name, parentId, description)
			val result = serverProjectsApi.upsertGroup(request, session.syncId)
			val error = result.exceptionOrNull()
			when {
				error == null -> {
					val serverId = result.getOrThrow().groupId
					if (serverId == def.id) {
						session.ledger.confirmedGroupIds += def.id
						session.ledger.recordGroup(def.id, GroupBaseline(def.name, parentId, description))
						session.onLog.log(SyncLogLevel.INFO, Res.string.sync_log_account_group_create_server_success, def.name)
					} else {
						adoptServerGroup(def, group.id, serverId, session)
					}
					return
				}

				error.apiErrorCode() == ApiErrorCode.GROUP_ID_TAKEN && !reminted -> {
					val newId = GroupId.randomUUID()
					def = rekey(def, group.id, newId, session) ?: return failCreate(group, session, null)
					reminted = true
					session.onLog.log(SyncLogLevel.WARN, Res.string.sync_log_account_group_id_reminted, def.name)
				}

				else -> return failCreate(group, session, error)
			}
		}
	}

	/**
	 * The server matched [def] by name to its existing group [serverId]. Its baseline is left for the
	 * next session, which settles any difference the server's way.
	 */
	private suspend fun adoptServerGroup(def: GroupDef, originalId: GroupId, serverId: GroupId, session: Session) {
		if (serverId in session.localIds) {
			Napier.w("'${def.name}' not adopting server group ${serverId.id}: another local group holds it")
			return failCreate(def, session, null)
		}
		rekey(def, originalId, serverId, session) ?: return failCreate(def, session, null)
		session.ledger.confirmedGroupIds += serverId
		session.claimed += serverId
		session.onLog.log(SyncLogLevel.INFO, Res.string.sync_log_account_group_adopt_server, def.name)
	}

	private fun rekey(def: GroupDef, originalId: GroupId, newId: GroupId, session: Session): GroupDef? =
		try {
			projectsRepository.setGroupId(def, newId).also {
				session.ledger.rekeyGroup(def.id, newId)
				session.localIds -= def.id
				session.localIds += newId
				session.remap[originalId] = newId
			}
		} catch (e: IOException) {
			Napier.e("Failed to change the id of group '${def.name}'", e)
			null
		}

	private suspend fun failCreate(group: GroupDef, session: Session, error: Throwable?) {
		session.failed += group.id
		error?.let { Napier.e("Failed to create group on server: ${group.name}", it) }
		session.onLog.log(SyncLogLevel.ERROR, Res.string.sync_log_account_group_create_server_failure, group.name)
		if (error.isAuthenticationFailure()) session.onUnauthorized()
	}

	/** Parents first; a group whose parent is missing locally is skipped. */
	private suspend fun createLocally(groups: List<ApiGroupDefinition>, session: Session) {
		if (groups.isEmpty()) return
		val tree = projectsRepository.getProjectTree()
		val created = mutableMapOf<GroupId, GroupDef>()
		groups.forEach { remote ->
			val parent = remote.parentId?.let { created[it] ?: tree.group(it) }
			if (remote.parentId != null && parent == null) {
				session.onLog.log(SyncLogLevel.WARN, Res.string.sync_log_account_group_skipped_parent_failed, remote.name)
				return@forEach
			}
			createLocally(remote, parent, session)?.let { created[remote.uuid] = it }
		}
	}

	private suspend fun createLocally(remote: ApiGroupDefinition, parent: GroupDef?, session: Session): GroupDef? {
		val description = remote.description.orNullIfBlank()
		val baseName = ProjectsRepository.toLocalSafeName(remote.name)
		var name = baseName
		var result = projectsRepository.createGroup(name, parent, remote.uuid, description)
		var suffix = 2
		while (result.failureReason() == GroupOperationFailed.Reason.AlreadyExists && suffix <= MAX_NAME_SUFFIX) {
			name = "$baseName ($suffix)"
			result = projectsRepository.createGroup(name, parent, remote.uuid, description)
			suffix++
		}

		if (!isSuccess(result)) {
			session.onLog.log(SyncLogLevel.ERROR, Res.string.sync_log_account_group_create_client_failure, remote.name)
			return null
		}

		val def = result.data
		session.localIds += def.id
		session.onLog.log(SyncLogLevel.INFO, Res.string.sync_log_account_group_create_client_success, def.name)
		if (name == baseName) {
			session.ledger.recordGroup(def.id, GroupBaseline(remote.name, remote.parentId, description))
		} else {
			// The other devices take the suffixed name too, or this one would rename it back. The
			// name-taken log already covers it, so the push does not add its update-success line.
			session.onLog.log(SyncLogLevel.WARN, Res.string.sync_log_account_group_name_taken_client, remote.name, name)
			val renamed = ApiGroupDefinition(def.id, name, remote.parentId, description)
			if (pushUpdate(renamed, session, logSuccess = false)) {
				session.ledger.recordGroup(def.id, GroupBaseline(name, remote.parentId, description))
			}
		}
		return def
	}

	/** By server depth, parents first, so a group moving under another finds it in place. */
	private suspend fun applyUpdates(updates: List<GroupUpdate>, session: Session) {
		if (updates.isEmpty()) return
		var tree = projectsRepository.getProjectTree()
		updates.forEach { planned ->
			val local = tree.group(planned.local.id) ?: return@forEach
			// Re-read, since a parent created on the server this session may have taken a new id.
			val update = planned.copy(local = local)
			if (projectsRepository.isGroupInUse(local)) {
				session.onLog.log(SyncLogLevel.WARN, Res.string.sync_log_account_group_in_use, local.name)
				return@forEach
			}

			var settled = true
			if (update.needsServerPush) {
				// The server would put a group under a parent it does not hold at the top level.
				val parentBlocked = update.parent == Winner.PushLocal &&
					update.mergedParent != null && update.mergedParent !in session.ledger.confirmedGroupIds
				val parent = if (parentBlocked) update.server.parentId else update.mergedParent
				if (parentBlocked) settled = false
				val request = ApiGroupDefinition(local.id, update.mergedName, parent, update.mergedDescription)
				if (!pushUpdate(request, session)) settled = false
			}

			val changedLocally = applyServerValues(local, update, tree, session)
			if (changedLocally != null) {
				if (!changedLocally) settled = false
				tree = projectsRepository.getProjectTree()
			}

			if (settled) session.ledger.recordGroup(local.id, update.baseline)
		}
	}

	/** Returns null when nothing needed applying, else whether every change succeeded. */
	private suspend fun applyServerValues(local: GroupDef, update: GroupUpdate, tree: ProjectTree, session: Session): Boolean? {
		val remote = update.server
		if (update.name != Winner.TakeServer && update.parent != Winner.TakeServer &&
			update.description != Winner.TakeServer
		) {
			return null
		}

		var def = local
		if (update.name == Winner.TakeServer) {
			val result = projectsRepository.renameGroup(def, ProjectsRepository.toLocalSafeName(remote.name))
			if (!isSuccess(result)) return failLocalUpdate(local, result, session)
			def = result.data
		}
		if (update.parent == Winner.TakeServer) {
			val target = remote.parentId?.let { tree.group(it) }
			if (remote.parentId != null && target == null) return failLocalUpdate(def, null, session)
			val result = projectsRepository.moveGroup(def, target)
			if (!isSuccess(result)) return failLocalUpdate(def, result, session)
			def = result.data
		}
		if (update.description == Winner.TakeServer) {
			val result = projectsRepository.updateGroupDescription(def, remote.description)
			if (!isSuccess(result)) return failLocalUpdate(def, result, session)
			def = result.data
		}
		session.onLog.log(SyncLogLevel.INFO, Res.string.sync_log_account_group_update_client_from_server_success, def.name)
		return true
	}

	private suspend fun failLocalUpdate(def: GroupDef, result: CResult<*>?, session: Session): Boolean {
		if (result?.failureReason() == GroupOperationFailed.Reason.GroupInUse) {
			session.onLog.log(SyncLogLevel.WARN, Res.string.sync_log_account_group_in_use, def.name)
		} else {
			session.onLog.log(SyncLogLevel.ERROR, Res.string.sync_log_account_group_update_client_from_server_failure, def.name)
		}
		return false
	}

	private suspend fun pushUpdate(group: ApiGroupDefinition, session: Session, logSuccess: Boolean = true): Boolean {
		val result = serverProjectsApi.upsertGroup(group, session.syncId)
		val error = result.exceptionOrNull()
		if (error == null) {
			if (logSuccess) session.onLog.log(SyncLogLevel.INFO, Res.string.sync_log_account_group_update_server_success, group.name)
			return true
		}

		Napier.e("Failed to update group on server: ${group.name}", error)
		val message = when (error.apiErrorCode()) {
			ApiErrorCode.GROUP_CYCLE -> Res.string.sync_log_account_group_update_server_cycle
			ApiErrorCode.GROUP_TOO_DEEP -> Res.string.sync_log_account_group_update_server_too_deep
			else -> Res.string.sync_log_account_group_update_server_failure
		}
		session.onLog.log(SyncLogLevel.ERROR, message, group.name)
		if (error.isAuthenticationFailure()) session.onUnauthorized()
		return false
	}

	/**
	 * Server deletions move the group's contents up to its parent on the server; local deletions do
	 * the same on disk, children first. A deleted group's baseline is retired.
	 */
	suspend fun applyDeletions(
		syncId: String,
		pending: PendingGroupDeletes,
		ledger: BaselineLedger,
		onLog: OnSyncLog,
		onUnauthorized: suspend () -> Unit,
	) {
		pending.onServer.forEach { group ->
			val result = serverProjectsApi.deleteGroup(group.uuid, syncId)
			val error = result.exceptionOrNull()
			if (error == null) {
				ledger.retireGroup(group.uuid)
				onLog.log(SyncLogLevel.INFO, Res.string.sync_log_account_group_delete_server_success, group.name)
			} else {
				Napier.e("Failed to delete group on server: ${group.name}", error)
				onLog.log(SyncLogLevel.ERROR, Res.string.sync_log_account_group_delete_server_failure, group.name)
				if (error.isAuthenticationFailure()) onUnauthorized()
			}
		}

		if (pending.locally.isEmpty()) return
		val tree = projectsRepository.getProjectTree()
		pending.locally
			.mapNotNull { id -> tree.group(id).also { if (it == null) ledger.retireGroup(id) } }
			.sortedByDescending { tree.depthOf(it.id) }
			.forEach { group ->
				val result = projectsRepository.deleteGroup(group)
				when {
					isSuccess(result) -> {
						ledger.retireGroup(group.id)
						onLog.log(SyncLogLevel.INFO, Res.string.sync_log_account_group_delete_client_success, group.name)
					}

					result.failureReason() == GroupOperationFailed.Reason.GroupInUse ->
						onLog.log(SyncLogLevel.WARN, Res.string.sync_log_account_group_in_use, group.name)

					else -> onLog.log(SyncLogLevel.ERROR, Res.string.sync_log_account_group_delete_client_failure, group.name)
				}
			}
	}

	private suspend fun OnSyncLog.log(level: SyncLogLevel, message: StringResource, vararg args: Any) =
		this(syncAccLog(strRes.get(message, *args), level))

	private companion object {
		const val MAX_NAME_SUFFIX = 99
	}
}

private fun CResult<*>.failureReason(): GroupOperationFailed.Reason? =
	((this as? ClientResult.Failure<*>)?.exception as? GroupOperationFailed)?.reason
