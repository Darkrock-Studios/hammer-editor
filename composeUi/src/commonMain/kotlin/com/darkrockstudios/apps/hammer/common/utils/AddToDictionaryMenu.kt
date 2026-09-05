package com.darkrockstudios.apps.hammer.common.utils

import com.darkrockstudios.texteditor.contextmenu.ContextMenuItem
import com.darkrockstudios.texteditor.spellcheck.SpellCheckItem

/** Context-menu items for a flagged span: "Add to dictionary" on a misspelled word, nothing on sentence issues. */
fun addToDictionaryMenuItems(
	label: String,
	addWord: (String) -> Unit,
): (SpellCheckItem) -> List<ContextMenuItem> = { item ->
	if (item is SpellCheckItem.MisspelledWord) {
		listOf(ContextMenuItem(label = label) { addWord(item.segment.text) })
	} else {
		emptyList()
	}
}
