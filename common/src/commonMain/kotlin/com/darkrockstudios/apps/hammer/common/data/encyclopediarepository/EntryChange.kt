package com.darkrockstudios.apps.hammer.common.data.encyclopediarepository

import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContent

/**
 * A single entry mutation, carrying the written content so consumers can react
 * without re-reading the entry from disk. Complements [EncyclopediaRepository.entryContentChangedFlow],
 * which only signals that "something changed".
 */
sealed interface EntryChange {
	/** An entry was created or updated; [entry] is exactly what was persisted. */
	data class Saved(val entry: EntryContent) : EntryChange

	data class Deleted(val id: Int) : EntryChange

	/** Sync re-numbered an entry; its content is unchanged. */
	data class ReId(val oldId: Int, val newId: Int) : EntryChange
}
