package com.darkrockstudios.apps.hammer.common.data.references

import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType

sealed class ReferenceSourceType {
	data object Scene : ReferenceSourceType()
}

data class ReferenceIndexConfig(
	val enabledEntryTypes: Set<EntryType>,
	val enabledSourceTypes: Set<ReferenceSourceType>,
) {
	companion object {
		fun default() = ReferenceIndexConfig(
			enabledEntryTypes = setOf(EntryType.PERSON),
			enabledSourceTypes = setOf(ReferenceSourceType.Scene),
		)
	}
}
