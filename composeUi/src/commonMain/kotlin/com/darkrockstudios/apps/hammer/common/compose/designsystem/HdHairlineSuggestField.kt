package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * An [HdHairlineField] for free text with known values suggested under it: the chips are what
 * [suggest] returns for the text so far, and tapping one fills the field. Typing anything else is
 * fine; the suggestions only save retyping.
 *
 *     LANE                              optional
 *     Ali                                     ■
 *     ────────────────────────────────────────────
 *     [▪ Alice] [▪ Alice's size]
 *
 * [suggestionAccent] colours each chip's swatch; null shows a neutral `#`. [trailing] sits at the
 * end of the input, e.g. a swatch for a value that matches a known one.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HdHairlineSuggestField(
	label: String,
	value: String,
	onValueChange: (String) -> Unit,
	suggest: (prefix: String) -> List<String>,
	modifier: Modifier = Modifier,
	placeholder: String? = null,
	hint: String? = null,
	suggestionAccent: (String) -> Color? = { null },
	onFocusChanged: (Boolean) -> Unit = {},
	trailing: (@Composable () -> Unit)? = null,
	testTag: String? = null,
) {
	// Not remembered: the known values can change while the text stays the same.
	val suggestions = suggest(value)
	Column(modifier = modifier.fillMaxWidth()) {
		HdHairlineField(
			label = label,
			value = value,
			onValueChange = onValueChange,
			placeholder = placeholder,
			hint = hint,
			onFocusChanged = onFocusChanged,
			trailing = trailing,
			testTag = testTag,
		)
		if (suggestions.isNotEmpty()) {
			FlowRow(
				modifier = Modifier
					.fillMaxWidth()
					.padding(top = 6.dp)
					.then(if (testTag != null) Modifier.testTag("$testTag$SUGGESTIONS_TAG_SUFFIX") else Modifier),
				horizontalArrangement = Arrangement.spacedBy(6.dp),
				verticalArrangement = Arrangement.spacedBy(6.dp),
			) {
				suggestions.forEach { suggestion ->
					// Not focusable, so pressing a chip doesn't pull focus from the field first.
					HdTagChip(
						label = suggestion,
						onClick = { onValueChange(suggestion) },
						accent = suggestionAccent(suggestion),
						modifier = Modifier.focusProperties { canFocus = false },
					)
				}
			}
		}
	}
}

/** Appended to the field's test tag for its row of suggestions. */
const val SUGGESTIONS_TAG_SUFFIX = "-suggestions"
