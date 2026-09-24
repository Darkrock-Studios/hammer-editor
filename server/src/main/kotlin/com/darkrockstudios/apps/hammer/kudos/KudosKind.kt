package com.darkrockstudios.apps.hammer.kudos

enum class KudosGroup(val maxPicks: Int) {
	CRAFT(maxPicks = 4),
	REACTION(maxPicks = 1),
}

/**
 * The chips a reader can pick. [key] is what the database stores, so it must never change
 * once shipped; declaration order is display order.
 */
enum class KudosKind(val key: String, val group: KudosGroup) {
	PROSE("prose", KudosGroup.CRAFT),
	VOICE("voice", KudosGroup.CRAFT),
	CHARACTERS("characters", KudosGroup.CRAFT),
	DIALOGUE("dialogue", KudosGroup.CRAFT),
	PLOT("plot", KudosGroup.CRAFT),
	PACING("pacing", KudosGroup.CRAFT),
	WORLDBUILDING("worldbuilding", KudosGroup.CRAFT),
	ATMOSPHERE("atmosphere", KudosGroup.CRAFT),
	OPENING("opening", KudosGroup.CRAFT),
	ENDING("ending", KudosGroup.CRAFT),
	MOVED_ME("moved_me", KudosGroup.REACTION),
	MADE_ME_THINK("made_me_think", KudosGroup.REACTION),
	MADE_ME_LAUGH("made_me_laugh", KudosGroup.REACTION),
	PAGE_TURNER("page_turner", KudosGroup.REACTION);

	val messageKey: String get() = "kudos_kind_$key"

	companion object {
		private val byKey = entries.associateBy { it.key }

		fun fromKey(key: String): KudosKind? = byKey[key]
	}
}
