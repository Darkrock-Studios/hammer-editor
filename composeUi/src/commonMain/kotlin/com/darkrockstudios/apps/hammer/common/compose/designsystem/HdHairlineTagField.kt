package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.compose.CollapseWhileTyping
import com.darkrockstudios.apps.hammer.common.data.tagindex.isTagSeparator
import com.darkrockstudios.apps.hammer.common.data.tagindex.parseTagInput
import com.darkrockstudios.apps.hammer.common.data.tagindex.replaceTagPrefix
import com.darkrockstudios.apps.hammer.common.data.tagindex.tagPrefixOf

/**
 * Tag chip field — labeled hairline-underline input that keeps its
 * value as a list of tags rather than a free-form string. Each tag
 * renders as an [HdTagChip] with a `×` remove affordance; the trailing
 * input adds a tag on Enter or comma and removes the last tag on
 * Backspace when empty.
 *
 *     TAGS                              ↵ to add
 *     [# animal ×] [# guide ×]  ritual,…
 *     ────────────────────────────────────────────
 */
@OptIn(ExperimentalLayoutApi::class)
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
	var draft by remember { mutableStateOf("") }
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

	val onSurface = MaterialTheme.colorScheme.onSurface
	val muted = MaterialTheme.colorScheme.onSurfaceVariant
	val textStyle = MaterialTheme.typography.bodyMedium.copy(color = onSurface)

	// Collapse the whole field while the body editor is being typed into; keep it visible while it
	// holds focus itself so tapping it (which raises the keyboard) doesn't make it vanish.
	var tagsFocused by remember { mutableStateOf(false) }
	CollapseWhileTyping(modifier = modifier, keepVisible = tagsFocused) {
		Column(modifier = Modifier.fillMaxWidth()) {
			Row(
				modifier = Modifier.fillMaxWidth(),
				verticalAlignment = Alignment.Bottom,
				horizontalArrangement = Arrangement.spacedBy(10.dp),
			) {
				HdMonoLabel(text = label)
				if (hint != null) {
					HdMonoLabel(
						text = hint,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
					)
				}
			}

			// One scrollable line — chips + input share a fixed container so focus survives
			// the keyboard opening, and tags never wrap to extra rows that eat editor space.
			Row(
				modifier = Modifier
					.fillMaxWidth()
					.heightIn(min = 38.dp)
					.horizontalScroll(rememberScrollState())
					.padding(top = 6.dp, bottom = 6.dp),
				horizontalArrangement = Arrangement.spacedBy(6.dp),
				verticalAlignment = Alignment.CenterVertically,
			) {
				tags.forEach { tag ->
					HdTagChip(
						label = tag,
						active = true,
						onRemove = { onTagsChange(tags - tag) },
					)
				}
				Box(
					modifier = Modifier
						.width(200.dp)
						.heightIn(min = 24.dp)
						.padding(vertical = 2.dp),
					contentAlignment = Alignment.CenterStart,
				) {
					BasicTextField(
						value = draft,
						onValueChange = { next ->
							val last = next.lastOrNull()
							if (last != null && last.isTagSeparator()) {
								updateDraft(next.dropLast(1))
								addCurrent()
							} else {
								updateDraft(next)
							}
						},
						singleLine = true,
						textStyle = textStyle,
						cursorBrush = SolidColor(onSurface),
						keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
						modifier = Modifier
							.fillMaxWidth()
							.heightIn(min = 24.dp)
							.onFocusChanged { focusState ->
								tagsFocused = focusState.isFocused
								// A typed-but-uncommitted tag would otherwise be silently
								// dropped when focus moves on — commit it instead.
								if (!focusState.isFocused && draft.isNotBlank()) {
									addCurrent()
								}
							}
							.then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
							.onPreviewKeyEvent { event ->
								if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
								when (event.key) {
									Key.Enter, Key.NumPadEnter -> addCurrent()
									Key.Backspace -> removeLast()
									else -> false
								}
							},
					)
					if (draft.isEmpty() && tags.isEmpty() && placeholder != null) {
						Text(
							text = placeholder,
							style = textStyle.copy(color = muted),
						)
					}
				}
			}

			HorizontalDivider(
				thickness = Dp.Hairline,
				color = MaterialTheme.colorScheme.outlineVariant,
			)

			HdTagSuggestionStrip(
				suggestions = suggestions,
				onSelect = { addTag(replaceTagPrefix(draft, it)) },
				modifier = Modifier.padding(top = 6.dp),
			)
		}
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
