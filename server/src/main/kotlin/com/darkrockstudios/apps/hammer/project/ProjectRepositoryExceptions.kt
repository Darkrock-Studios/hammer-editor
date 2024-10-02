package com.darkrockstudios.apps.hammer.project

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
class ProjectNotFound(val projectDef: ProjectDefinition) :
	Exception("Project $projectDef not found on server")
