package com.darkrockstudios.apps.hammer.common.data.timelinerepository

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.russhwolf.settings.Settings

/** The timeline each project last showed, kept on this device only and never synced. */
class TimelineSelectionSetting(private val settings: Settings) {

	/** The id last saved for [projectId]; it may name a timeline deleted since. */
	fun load(projectId: ProjectId): String? = settings.getStringOrNull(key(projectId))

	fun save(projectId: ProjectId, timelineId: String) {
		settings.putString(key(projectId), timelineId)
	}

	private fun key(projectId: ProjectId) = "timeline.selected.${projectId.id}"
}
