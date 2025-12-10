package com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry

import com.darkrockstudios.apps.hammer.*
import org.jetbrains.compose.resources.StringResource

enum class EntryType(val text: String) {
	PERSON("person"),
	PLACE("place"),
	THING("thing"),
	EVENT("event"),
	IDEA("idea");

	override fun toString() = text

	fun toStringResource(): StringResource {
		return Companion.toStringResource(this)
	}

	companion object {
		fun fromString(string: String): EntryType {
			val sanitized = string.trim().lowercase()
			return when (sanitized) {
				PERSON.text -> PERSON
				PLACE.text -> PLACE
				THING.text -> THING
				EVENT.text -> EVENT
				IDEA.text -> IDEA
				else -> throw IllegalArgumentException("Failed to parse EntryType from input: '$string' '${PERSON.text}'")
			}
		}

		fun toStringResource(type: EntryType): StringResource {
			return when (type) {
				PERSON -> Res.string.encyclopedia_category_person
				PLACE -> Res.string.encyclopedia_category_place
				THING -> Res.string.encyclopedia_category_thing
				EVENT -> Res.string.encyclopedia_category_event
				IDEA -> Res.string.encyclopedia_category_idea
			}
		}
	}
}