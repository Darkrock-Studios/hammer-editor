package com.darkrockstudios.apps.hammer.base.http.projectdata

import kotlinx.serialization.Serializable

@Serializable
data class ProjectData(
	val authorName: String? = null,
	val theme: ProjectTheme? = null,
	val wordCountGoal: WordCountGoal? = null,
	/** Picked as a unit during conflict resolution — the whole set comes from one side, never a merge. */
	val tags: Set<String> = emptySet(),
	/** BCP-47 tag of the language the project is written in (e.g. "en", "pt-BR"); null when unset. */
	val language: String? = null,
	/** Whether Encyclopedia entry names feed the spell-check session dictionary for this project. */
	val encyclopediaDictionary: Boolean = true,
	/** User-added spell-check words. Unlike [tags], a conflict merges both sides by union. */
	val dictionaryWords: Set<String> = emptySet(),
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
