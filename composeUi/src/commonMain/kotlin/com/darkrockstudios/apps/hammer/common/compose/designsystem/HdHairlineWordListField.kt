package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Word list field — labeled hairline-underline input whose value is a
 * list of single words, each rendered as a neutral [HdTagChip] with a
 * `×` remove affordance. Chips wrap, so a long list grows downward
 * rather than scrolling sideways. The trailing input commits on Enter,
 * comma, or space, and removes the last word on Backspace when empty.
 * [parseInput] is the same rule persistence applies, so a chip is never
 * shown for input that would be dropped on save.
 *
 *     PROJECT DICTIONARY                      ↵ to add
 *     [# Kvothe ×] [# Denna ×] [# Tarbean ×]
 *     [# Imre ×]  Ademre…
 *     ────────────────────────────────────────────
 */
@OptIn(ExperimentalLayoutApi::class)
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

	val commitDraft: () -> Boolean = commit@{
		val word = parseInput(draft) ?: return@commit false
		if (word !in words) onAdd(word)
		draft = ""
		true
	}
	val removeLast: () -> Boolean = remove@{
		if (draft.isNotEmpty() || words.isEmpty()) return@remove false
		onRemove(words.last())
		true
	}

	val onSurface = MaterialTheme.colorScheme.onSurface
	val muted = MaterialTheme.colorScheme.onSurfaceVariant
	val textStyle = MaterialTheme.typography.bodyMedium.copy(color = onSurface)

	Column(modifier = modifier.fillMaxWidth()) {
		Row(
			modifier = Modifier.fillMaxWidth(),
			verticalAlignment = Alignment.Bottom,
			horizontalArrangement = Arrangement.spacedBy(10.dp),
		) {
			HdMonoLabel(text = label)
			if (hint != null) {
				HdMonoLabel(text = hint, color = muted)
			}
		}

		FlowRow(
			modifier = Modifier
				.fillMaxWidth()
				.heightIn(min = 38.dp)
				.padding(top = 6.dp, bottom = 6.dp),
			horizontalArrangement = Arrangement.spacedBy(6.dp),
			verticalArrangement = Arrangement.spacedBy(6.dp),
		) {
			words.forEach { word ->
				HdTagChip(
					label = word,
					active = true,
					accent = null,
					onRemove = { onRemove(word) },
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
						if (last != null && (last == ',' || last.isWhitespace())) {
							draft = next.dropLast(1)
							commitDraft()
						} else {
							draft = next
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
							// A typed-but-uncommitted word would otherwise be silently
							// dropped when focus moves on — commit it instead.
							if (!focusState.isFocused && draft.isNotBlank()) {
								commitDraft()
							}
						}
						.then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
						.onPreviewKeyEvent { event ->
							if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
							when (event.key) {
								Key.Enter, Key.NumPadEnter -> commitDraft()
								Key.Backspace -> removeLast()
								else -> false
							}
						},
				)
				if (draft.isEmpty() && words.isEmpty() && placeholder != null) {
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
	}
}
