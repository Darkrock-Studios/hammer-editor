package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.darkrockstudios.apps.hammer.common.data.tagindex.isTagSeparator

/**
 * Word list field: the tag field's shape for a plain list of single words, such as the
 * project dictionary. Neutral chips wrap onto further rows; a separator (space, comma, or
 * their fullwidth forms), Enter, or focus loss commits the draft, and Backspace on an empty
 * draft removes the last chip. [parseInput] is the same rule persistence applies, so a chip
 * is never shown for input that would be dropped on save; input it rejects stays in the
 * draft where the user can see it. Duplicates are matched ignoring case.
 *
 *     PROJECT DICTIONARY                      to add
 *     [# Kvothe x] [# Denna x] [# Tarbean x]
 *     [# Imre x]  Ademre
 *     ----------------------------------------------
 */
@Composable
fun HdHairlineWordListField(
	label: String,
	words: List<String>,
	onAdd: (String) -> Unit,
	onRemove: (String) -> Unit,
	parseInput: (String) -> String?,
	modifier: Modifier = Modifier,
	hint: String? = null,
	placeholder: String? = null,
	testTag: String? = null,
) {
	// Saveable so a configuration change can't drop a word that was typed but not yet committed.
	var draft by rememberSaveable { mutableStateOf("") }

	/** Commits what the parser accepts and returns the tokens it rejected. */
	fun commitAll(tokens: List<String>): List<String> = tokens.filterNot { token ->
		val word = parseInput(token) ?: return@filterNot false
		if (words.none { it.equals(word, ignoreCase = true) }) onAdd(word)
		true
	}

	val onInput: (String) -> Unit = { next ->
		if (next.any { it.isTagSeparator() }) {
			// A separator anywhere commits the complete words before it, so a pasted
			// list is not stranded as one draft the parser rejects.
			val tokens = splitOnSeparators(next)
			val endsWithSeparator = next.last().isTagSeparator()
			val complete = if (endsWithSeparator) tokens else tokens.dropLast(1)
			val partial = if (endsWithSeparator) emptyList() else tokens.takeLast(1)
			draft = (commitAll(complete) + partial).joinToString(" ")
		} else {
			draft = next
		}
	}
	val commitDraft: () -> Boolean = commit@{
		val tokens = splitOnSeparators(draft)
		val rejected = commitAll(tokens)
		draft = rejected.joinToString(" ")
		rejected.size < tokens.size
	}
	val removeLast: () -> Boolean = remove@{
		if (draft.isNotEmpty() || words.isEmpty()) return@remove false
		onRemove(words.last())
		true
	}

	HdHairlineChipInput(
		label = label,
		chips = words,
		draft = draft,
		onInput = onInput,
		onCommitDraft = commitDraft,
		onRemoveLast = removeLast,
		onRemoveChip = onRemove,
		modifier = modifier,
		hint = hint,
		placeholder = placeholder,
		wrapChips = true,
		accentChips = false,
		testTag = testTag,
	)
}

private fun splitOnSeparators(text: String): List<String> {
	val tokens = mutableListOf<String>()
	val current = StringBuilder()
	for (c in text) {
		if (c.isTagSeparator()) {
			if (current.isNotEmpty()) tokens += current.toString()
			current.clear()
		} else {
			current.append(c)
		}
	}
	if (current.isNotEmpty()) tokens += current.toString()
	return tokens
}
