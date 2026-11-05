package com.darkrockstudios.apps.hammer.base.timeline

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.random.Random

/** [code] is hashed; never reuse or renumber one. */
@Serializable
enum class TimelineMode(val code: Int) {
	/** Drag order is the truth; an event's label is its shown "when". */
	@SerialName("manual")
	MANUAL(0),

	/** Every entry has a start date and the order derives from it. */
	@SerialName("dated")
	DATED(1),
}

@Serializable
data class TimelineDef(
	val id: String,
	val name: String,
	val mode: TimelineMode = TimelineMode.MANUAL,
	val calendar: CalendarDef? = null,
	val laneOrder: List<String> = emptyList(),
) {
	companion object {
		const val MAIN_ID = "main"
		val DEFAULT = TimelineDef(id = MAIN_ID, name = "Main")

		/** 8 lowercase hex chars. */
		fun randomId(random: Random = Random.Default): String =
			random.nextInt().toUInt().toString(16).padStart(8, '0')
	}
}
