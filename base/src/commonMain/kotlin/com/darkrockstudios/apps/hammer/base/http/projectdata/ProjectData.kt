package com.darkrockstudios.apps.hammer.base.http.projectdata

import kotlinx.serialization.Serializable

@Serializable
data class ProjectData(
	val authorName: String? = null,
	val theme: ProjectTheme? = null,
	val wordCountGoal: WordCountGoal? = null,
)

/** Picked as a unit during conflict resolution — one device's primary paired with another's secondary is undesigned. */
@Serializable
data class ProjectTheme(
	val primary: String,
	val secondary: String,
)

/** Picked as a unit during conflict resolution — a count from one cadence with the other's cadence silently 7x's the goal. */
@Serializable
data class WordCountGoal(
	val cadence: Cadence,
	val count: Int,
) {
	@Serializable
	enum class Cadence { DAY, WEEK }
}
