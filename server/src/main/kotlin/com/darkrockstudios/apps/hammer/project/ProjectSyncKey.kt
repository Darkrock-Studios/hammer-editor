package com.darkrockstudios.apps.hammer.project

import com.darkrockstudios.apps.hammer.base.ProjectId

/** One project session per project, whoever holds it; the session records the holder. */
data class ProjectSyncKey(val projectId: ProjectId)
