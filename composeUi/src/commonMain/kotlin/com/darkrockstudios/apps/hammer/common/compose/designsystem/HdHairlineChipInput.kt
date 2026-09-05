package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
 * The body shared by [HdHairlineTagField] and [HdHairlineWordListField]: mono label and
 * hint, a run of removable [HdTagChip]s, a trailing draft input that commits on Enter or
 * focus loss and removes the last chip on Backspace when empty, and the hairline rule.
 * Parsing and persistence stay with the caller, which also decides what typed text does
 * through [onInput].
 *
 * @param onInput Receives every value change of the draft input.
 * @param onCommitDraft Commits the draft; returns whether the key event was consumed.
 * @param onRemoveLast Removes the last chip; returns whether the key event was consumed.
 * @param wrapChips Wrap chips onto further rows instead of scrolling one row sideways.
 * @param accentChips Give each chip its per-label color swatch instead of a neutral `#`.
 * @param footer Drawn under the rule, e.g. a suggestion strip.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HdHairlineChipInput(
	label: String,
	chips: List<String>,
	draft: String,
	onInput: (String) -> Unit,
	onCommitDraft: () -> Boolean,
	onRemoveLast: () -> Boolean,
	onRemoveChip: (String) -> Unit,
	modifier: Modifier = Modifier,
	hint: String? = null,
	placeholder: String? = null,
	wrapChips: Boolean = false,
	accentChips: Boolean = true,
	testTag: String? = null,
	onFocusChanged: (Boolean) -> Unit = {},
	footer: @Composable () -> Unit = {},
) {
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

		val content: @Composable () -> Unit = {
			chips.forEach { chip ->
				if (accentChips) {
					HdTagChip(label = chip, active = true, onRemove = { onRemoveChip(chip) })
				} else {
					HdTagChip(label = chip, active = true, accent = null, onRemove = { onRemoveChip(chip) })
				}
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
					onValueChange = onInput,
					singleLine = true,
					textStyle = textStyle,
					cursorBrush = SolidColor(onSurface),
					keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
					modifier = Modifier
						.fillMaxWidth()
						.heightIn(min = 24.dp)
						.onFocusChanged { focusState ->
							onFocusChanged(focusState.isFocused)
							// A typed-but-uncommitted value would otherwise be silently
							// dropped when focus moves on; commit it instead.
							if (!focusState.isFocused && draft.isNotBlank()) {
								onCommitDraft()
							}
						}
						.then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
						.onPreviewKeyEvent { event ->
							if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
							when (event.key) {
								Key.Enter, Key.NumPadEnter -> onCommitDraft()
								Key.Backspace -> onRemoveLast()
								else -> false
							}
						},
				)
				if (draft.isEmpty() && chips.isEmpty() && placeholder != null) {
					Text(
						text = placeholder,
						style = textStyle.copy(color = muted),
					)
				}
			}
		}

		if (wrapChips) {
			FlowRow(
				modifier = Modifier
					.fillMaxWidth()
					.heightIn(min = 38.dp)
					.padding(top = 6.dp, bottom = 6.dp),
				horizontalArrangement = Arrangement.spacedBy(6.dp),
				verticalArrangement = Arrangement.spacedBy(6.dp),
			) {
				content()
			}
		} else {
			// One scrollable line: chips and input share a fixed container so focus survives
			// the keyboard opening, and chips never wrap to extra rows that eat editor space.
			Row(
				modifier = Modifier
					.fillMaxWidth()
					.heightIn(min = 38.dp)
					.horizontalScroll(rememberScrollState())
					.padding(top = 6.dp, bottom = 6.dp),
				horizontalArrangement = Arrangement.spacedBy(6.dp),
				verticalAlignment = Alignment.CenterVertically,
			) {
				content()
			}
		}

		HorizontalDivider(
			thickness = Dp.Hairline,
			color = MaterialTheme.colorScheme.outlineVariant,
		)

		footer()
	}
}
