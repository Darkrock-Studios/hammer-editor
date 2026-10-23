package com.darkrockstudios.apps.hammer.project

import com.darkrockstudios.apps.hammer.base.GroupId
import com.darkrockstudios.apps.hammer.base.ProjectId

class InvalidSyncIdException : Exception("Invalid sync id")
class NoEntityTypeFound(val id: Int) : Exception("Could not find Type for Entity ID: $id")
class EntityNotFound(val id: Int) : Exception("Entity $id not found on server")
class EntityTypeConflictException(
	val id: Int,
	val existingType: String,
	val submittedType: String
) :
	Exception(
		"Entity type conflict for ID: $id\n" +
			" Existing Type: $existingType\n" +
			" Submitted Type: $submittedType"
	)

class EntityTooLargeException(
	val id: Int,
	val size: Int,
	val maxSize: Int,
) : Exception("Entity $id too large: $size bytes (max $maxSize)")

class ProjectNotFound(val projectId: ProjectId) :
	Exception("Project $projectId not found on server") {
	constructor(projectDef: ProjectDefinition) : this(projectDef.uuid)
}

class InvalidProjectName(val name: String) :
	Exception("Invalid project name: $name")

class ProjectNameTaken(val name: String) :
	Exception("This account already has a project named: $name")

class ProjectDeletedException(val projectId: ProjectId) :
	Exception("Project $projectId was deleted from this account")

class ProjectIdTakenException(val projectId: ProjectId) :
	Exception("Project id $projectId is already in use")

class InvalidGroupName(val name: String) :
	Exception("Invalid group name: $name")

class InvalidGroupDescription(val length: Int) :
	Exception("Group description too long: $length characters")

class GroupNameTaken(val name: String) :
	Exception("A sibling group is already named: $name")

class GroupCycleException(val groupId: GroupId) :
	Exception("Group $groupId cannot be moved inside itself")

class GroupTooDeepException(val groupId: GroupId) :
	Exception("Group $groupId would nest too deeply")

class GroupIdTakenException(val groupId: GroupId) :
	Exception("Group id $groupId is already in use")
