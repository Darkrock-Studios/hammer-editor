package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.compose.CollapseWhileTyping
import com.darkrockstudios.apps.hammer.common.data.tagindex.isTagSeparator
import com.darkrockstudios.apps.hammer.common.data.tagindex.parseTagInput
import com.darkrockstudios.apps.hammer.common.data.tagindex.replaceTagPrefix
import com.darkrockstudios.apps.hammer.common.data.tagindex.tagPrefixOf

/**
 * Tag chip field: labeled hairline-underline input that keeps its value as a list of
 * tags rather than a free-form string. Each tag renders as an [HdTagChip] with a `×`
 * remove affordance; the trailing input adds a tag on Enter or comma and removes the
 * last tag on Backspace when empty. Built on [HdHairlineChipInput].
 *
 *     TAGS                              ↵ to add
 *     [# animal ×] [# guide ×]  ritual,…
 *     ────────────────────────────────────────────
 */
@Composable
fun HdHairlineTagField(
	label: String,
	tags: List<String>,
	onTagsChange: (List<String>) -> Unit,
	modifier: Modifier = Modifier,
	hint: String? = null,
	placeholder: String? = null,
	suggestTags: (prefix: String) -> List<String> = { emptyList() },
	testTag: String? = null,
	onDraftChange: (String) -> Unit = {},
) {
	// Saveable so a configuration change can't drop a tag that was typed but not yet committed.
	var draft by rememberSaveable { mutableStateOf("") }
	val updateDraft: (String) -> Unit = {
		draft = it
		onDraftChange(it)
	}

	// Parsed with the same rule that persistence uses, so a chip is never shown for input
	// that would be dropped on save.
	val addTag: (String) -> Unit = { raw ->
		val candidates = parseTagInput(raw).filter { it !in tags }
		if (candidates.isNotEmpty()) {
			onTagsChange(tags + candidates)
		}
		updateDraft("")
	}
	val addCurrent: () -> Boolean = add@{
		if (parseTagInput(draft).isEmpty()) return@add false
		addTag(draft)
		true
	}
	val removeLast: () -> Boolean = remove@{
		if (draft.isNotEmpty() || tags.isEmpty()) return@remove false
		onTagsChange(tags.dropLast(1))
		true
	}

	val suggestions = rememberTagSuggestions(draft, tags, suggestTags)

	// Collapse the whole field while the body editor is being typed into; keep it visible while it
	// holds focus itself so tapping it (which raises the keyboard) doesn't make it vanish.
	var tagsFocused by remember { mutableStateOf(false) }
	CollapseWhileTyping(modifier = modifier, keepVisible = tagsFocused) {
		HdHairlineChipInput(
			label = label,
			chips = tags,
			draft = draft,
			onInput = { next ->
				val last = next.lastOrNull()
				if (last != null && last.isTagSeparator()) {
					updateDraft(next.dropLast(1))
					addCurrent()
				} else {
					updateDraft(next)
				}
			},
			onCommitDraft = addCurrent,
			onRemoveLast = removeLast,
			onRemoveChip = { onTagsChange(tags - it) },
			hint = hint,
			placeholder = placeholder,
			testTag = testTag,
			onFocusChanged = { tagsFocused = it },
			footer = {
				HdTagSuggestionStrip(
					suggestions = suggestions,
					onSelect = { addTag(replaceTagPrefix(draft, it)) },
					modifier = Modifier.padding(top = 6.dp),
				)
			},
		)
	}
}

/**
 * Suggestions for the tag being typed at the end of [draft], minus the ones in [applied]. Pair the
 * strip's `onSelect` with [replaceTagPrefix] so picking one keeps the tags typed before it.
 */
@Composable
fun rememberTagSuggestions(
	draft: String,
	applied: Collection<String>,
	suggestTags: (prefix: String) -> List<String>,
): List<String> = remember(draft, applied, suggestTags) {
	val prefix = tagPrefixOf(draft)
	if (prefix.isEmpty()) emptyList()
	else suggestTags(prefix).filter { it !in applied }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HdTagSuggestionStrip(
	suggestions: List<String>,
	onSelect: (String) -> Unit,
	modifier: Modifier = Modifier,
) {
	if (suggestions.isEmpty()) return
	FlowRow(
		modifier = modifier.fillMaxWidth(),
		horizontalArrangement = Arrangement.spacedBy(6.dp),
		verticalArrangement = Arrangement.spacedBy(6.dp),
	) {
		suggestions.forEach { tag ->
			// A focusable chip takes focus on press, which commits the raw draft as a tag and
			// removes the strip before the release ever reaches onSelect.
			HdTagChip(
				label = tag,
				active = false,
				onClick = { onSelect(tag) },
				modifier = Modifier.focusProperties { canFocus = false },
			)
		}
	}
}
