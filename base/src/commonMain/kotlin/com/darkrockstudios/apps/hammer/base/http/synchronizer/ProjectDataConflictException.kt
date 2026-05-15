package com.darkrockstudios.apps.hammer.base.http.synchronizer

import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectDataConflictDto

class ProjectDataConflictException(val conflict: ProjectDataConflictDto) :
	Exception("Project data conflict")
