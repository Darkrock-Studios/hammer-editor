package com.darkrockstudios.apps.hammer.common.utils

import com.darkrockstudios.apps.hammer.common.spellcheck.normalizeDictionaryWord
import com.darkrockstudios.texteditor.contextmenu.ContextMenuItem
import com.darkrockstudios.texteditor.spellcheck.SpellCheckItem

/**
 * Context-menu items for a flagged span: "Add to dictionary" on a misspelled word the
 * dictionary can store, nothing on sentence issues or unstorable tokens.
 */
fun addToDictionaryMenuItems(
	label: String,
	addWord: (String) -> Unit,
): (SpellCheckItem) -> List<ContextMenuItem> = { item ->
	val word = (item as? SpellCheckItem.MisspelledWord)
		?.let { normalizeDictionaryWord(it.segment.text) }
	if (word != null) listOf(ContextMenuItem(label = label) { addWord(word) }) else emptyList()
}
