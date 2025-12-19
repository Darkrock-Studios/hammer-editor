package com.darkrockstudios.apps.hammer.project

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

class ProjectNotFound(val projectId: ProjectId) :
	Exception("Project $projectId not found on server") {
	constructor(projectDef: ProjectDefinition) : this(projectDef.uuid)
}

class InvalidProjectName(val name: String) :
	Exception("Invalid project name: $name")
