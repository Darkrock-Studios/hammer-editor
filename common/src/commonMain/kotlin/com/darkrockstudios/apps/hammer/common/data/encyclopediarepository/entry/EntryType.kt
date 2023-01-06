package com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry

enum class EntryType(val text: String) {
	PERSON("person"),
	PLACE("place"),
	THING("thing"),
	EVENT("event");

	companion object {
		fun fromString(string: String): EntryType {
			val sanitized = string.trim().lowercase()
			return when (sanitized) {
				PERSON.text -> PERSON
				PLACE.text -> PLACE
				THING.text -> THING
				EVENT.text -> EVENT
				else -> throw IllegalArgumentException("Failed to parse EntryType from input: '$string' '${PERSON.text}'")
			}
		}
	}
}