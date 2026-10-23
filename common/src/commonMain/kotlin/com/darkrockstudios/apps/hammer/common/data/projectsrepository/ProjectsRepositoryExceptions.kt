package com.darkrockstudios.apps.hammer.common.data.projectsrepository

import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.base.http.MAX_GROUP_NESTING_DEPTH
import com.darkrockstudios.apps.hammer.common.data.CResult
import com.darkrockstudios.apps.hammer.common.data.ClientMessage
import com.darkrockstudios.apps.hammer.common.data.toMsg
import com.darkrockstudios.apps.hammer.group_error_already_exists
import com.darkrockstudios.apps.hammer.group_error_in_use
import com.darkrockstudios.apps.hammer.group_error_invalid_name
import com.darkrockstudios.apps.hammer.group_error_move_failed
import com.darkrockstudios.apps.hammer.group_error_source_missing
import com.darkrockstudios.apps.hammer.group_error_target_missing
import com.darkrockstudios.apps.hammer.group_error_too_deep
import com.darkrockstudios.apps.hammer.group_error_would_cycle
import com.darkrockstudios.apps.hammer.project_rename_error_already_exists
import com.darkrockstudios.apps.hammer.project_rename_error_in_use
import com.darkrockstudios.apps.hammer.project_rename_error_invalid_name
import com.darkrockstudios.apps.hammer.project_rename_error_move_failed
import com.darkrockstudios.apps.hammer.project_rename_error_source_missing
import com.darkrockstudios.apps.hammer.project_rename_error_target_missing
import org.jetbrains.compose.resources.StringResource

class ValidationFailedException(val errorMessage: StringResource) : IllegalArgumentException()
class ProjectCreationFailedException(val errorMessage: StringResource?) : IllegalArgumentException()

/** A failed project rename or move. */
class ProjectRenameFailed(val reason: Reason) : Exception() {
	enum class Reason {
		InvalidName, AlreadyExists, SourceDoesNotExist, TargetMissing, MoveFailed, ProjectInUse
	}
}

class GroupOperationFailed(val reason: Reason) : Exception() {
	enum class Reason {
		InvalidName, AlreadyExists, SourceDoesNotExist, TargetMissing, MoveFailed, GroupInUse, WouldCycle, TooDeep
	}
}

/** A project's identity may not change while it has a project scope open. */
class ProjectInUseException(projectName: String) :
	IllegalStateException("Project is in use, its id cannot change: $projectName")

internal fun <T> projectRenameFailure(reason: ProjectRenameFailed.Reason): CResult<T> =
	CResult.failure(
		error = reason.name,
		displayMessage = when (reason) {
			ProjectRenameFailed.Reason.InvalidName -> Res.string.project_rename_error_invalid_name
			ProjectRenameFailed.Reason.AlreadyExists -> Res.string.project_rename_error_already_exists
			ProjectRenameFailed.Reason.SourceDoesNotExist -> Res.string.project_rename_error_source_missing
			ProjectRenameFailed.Reason.TargetMissing -> Res.string.project_rename_error_target_missing
			ProjectRenameFailed.Reason.MoveFailed -> Res.string.project_rename_error_move_failed
			ProjectRenameFailed.Reason.ProjectInUse -> Res.string.project_rename_error_in_use
		}.toMsg(),
		exception = ProjectRenameFailed(reason),
	)

internal fun <T> groupFailure(reason: GroupOperationFailed.Reason): CResult<T> =
	CResult.failure(
		error = reason.name,
		displayMessage = when (reason) {
			GroupOperationFailed.Reason.InvalidName -> Res.string.group_error_invalid_name.toMsg()
			GroupOperationFailed.Reason.AlreadyExists -> Res.string.group_error_already_exists.toMsg()
			GroupOperationFailed.Reason.SourceDoesNotExist -> Res.string.group_error_source_missing.toMsg()
			GroupOperationFailed.Reason.TargetMissing -> Res.string.group_error_target_missing.toMsg()
			GroupOperationFailed.Reason.MoveFailed -> Res.string.group_error_move_failed.toMsg()
			GroupOperationFailed.Reason.GroupInUse -> Res.string.group_error_in_use.toMsg()
			GroupOperationFailed.Reason.WouldCycle -> Res.string.group_error_would_cycle.toMsg()
			GroupOperationFailed.Reason.TooDeep ->
				ClientMessage.Resource(Res.string.group_error_too_deep, MAX_GROUP_NESTING_DEPTH)
		},
		exception = GroupOperationFailed(reason),
	)
