package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.hd_field_optional
import com.darkrockstudios.apps.hammer.common.compose.resources.get

/**
 * Placeholder text in every field: the [outline] gray faded toward the surface, well quieter
 * than labels, hints and disabled values, so an empty field never reads as filled in.
 */
val hdPlaceholderColor: Color
	@Composable get() = MaterialTheme.colorScheme.outline.copy(alpha = 0.75f)

/**
 * The design system's text field: a chrome-less input on a hairline underline, with a
 * sentence-case mono [label] above.
 *
 * With a [placeholder] (an example value, e.g. `hammer.ink`), the label stays up and the
 * example is the hint. Without one, the label sits in the empty field as the hint and floats up
 * once there is text. [hint] is help text below the field, replaced by [error] when set. [counter]
 * sits at the end of the label row. With a blank [label] and no [counter], the label row is left
 * out, for a caller that labels the field itself.
 *
 *     Server                                0/60
 *     hammer.ink
 *     ────────────────────────────────────────────
 *     Help text, or the error in red
 */
@Composable
fun HdHairlineField(
	label: String,
	value: String,
	onValueChange: (String) -> Unit,
	modifier: Modifier = Modifier,
	placeholder: String? = null,
	hint: String? = null,
	counter: String? = null,
	error: String? = null,
	singleLine: Boolean = true,
	minLines: Int = 1,
	maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
	imeAction: ImeAction = if (singleLine) ImeAction.Next else ImeAction.Default,
	/** Fires on the IME action key (Done, Go, Send, Search); use it to submit a form on Enter. */
	onImeAction: (() -> Unit)? = null,
	capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
	autoCorrectEnabled: Boolean? = null,
	keyboardType: KeyboardType = KeyboardType.Text,
	visualTransformation: VisualTransformation = VisualTransformation.None,
	/** Replaces the body style of the input; the color still follows [enabled]. */
	textStyle: TextStyle? = null,
	enabled: Boolean = true,
	/** Grabs focus on first composition; for the first field of a dialog. */
	autoFocus: Boolean = false,
	/** Marks the label "· optional". */
	optional: Boolean = false,
	onFocusChanged: (Boolean) -> Unit = {},
	trailing: (@Composable () -> Unit)? = null,
	testTag: String? = null,
) {
	val colors = MaterialTheme.colorScheme
	val isError = !error.isNullOrEmpty()
	val hasLabel = label.isNotBlank()
	val labelFloated = !hasLabel || value.isNotEmpty() || !placeholder.isNullOrEmpty()
	val labelShown by animateFloatAsState(if (labelFloated) 1f else 0f, label = "hdFieldLabel")
	val focusRequester = remember { FocusRequester() }
	if (autoFocus) {
		LaunchedEffect(Unit) { focusRequester.requestFocus() }
	}

	Column(modifier = modifier.fillMaxWidth()) {
		if (hasLabel || counter != null) Row(
			modifier = Modifier.fillMaxWidth(),
			verticalAlignment = Alignment.Bottom,
			horizontalArrangement = Arrangement.spacedBy(10.dp),
		) {
			HdFieldLabel(
				text = if (optional) "$label · ${Res.string.hd_field_optional.get()}" else label,
				color = if (isError) colors.error else colors.onSurfaceVariant,
				modifier = Modifier
					.weight(1f)
					.graphicsLayer {
						alpha = labelShown
						translationY = (1f - labelShown) * 6.dp.toPx()
					}
					.then(if (labelFloated) Modifier else Modifier.clearAndSetSemantics {}),
			)
			if (counter != null) {
				HdMetaText(text = counter)
			}
		}
		val textColor = if (enabled) colors.onSurface else colors.onSurfaceVariant
		val textStyle = (textStyle ?: if (singleLine) MaterialTheme.typography.bodyLarge
		else MaterialTheme.typography.bodyMedium)
			.copy(color = textColor)
		val emptyHint = placeholder?.takeIf { it.isNotEmpty() } ?: label.takeIf { hasLabel }

		// In a tonal region the input is a well, which replaces the underline.
		val inRegion = LocalHdInRegion.current
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.padding(top = 6.dp)
				.then(
					if (inRegion) Modifier
						.background(colors.background)
						.padding(horizontal = 10.dp, vertical = 8.dp)
					else Modifier.padding(bottom = 6.dp)
				),
			verticalAlignment = Alignment.CenterVertically,
		) {
			Box(modifier = Modifier.weight(1f)) {
				BasicTextField(
					value = value,
					onValueChange = onValueChange,
					modifier = Modifier
						.fillMaxWidth()
						.focusRequester(focusRequester)
						.onFocusChanged { onFocusChanged(it.isFocused) }
						.then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
					enabled = enabled,
					singleLine = singleLine,
					minLines = minLines,
					maxLines = maxLines,
					textStyle = textStyle,
					cursorBrush = SolidColor(colors.onSurface),
					visualTransformation = visualTransformation,
					keyboardOptions = KeyboardOptions(
						imeAction = imeAction,
						capitalization = capitalization,
						autoCorrectEnabled = autoCorrectEnabled,
						keyboardType = keyboardType,
					),
					keyboardActions = if (onImeAction != null) KeyboardActions(
						onDone = { onImeAction() },
						onGo = { onImeAction() },
						onSend = { onImeAction() },
						onSearch = { onImeAction() },
					) else KeyboardActions.Default,
				)
				if (value.isEmpty() && emptyHint != null) {
					Text(
						text = emptyHint,
						style = textStyle.copy(color = hdPlaceholderColor),
					)
				}
			}
			if (trailing != null) {
				Spacer(modifier = Modifier.size(8.dp))
				trailing()
			}
		}
		if (!inRegion || isError) {
			HorizontalDivider(
				thickness = Dp.Hairline,
				color = if (isError) colors.error else colors.outlineVariant,
			)
		}
		val below = error?.takeIf { it.isNotEmpty() } ?: hint
		if (!below.isNullOrEmpty()) {
			Text(
				text = below,
				style = MaterialTheme.typography.bodySmall,
				color = if (isError) colors.error else colors.onSurfaceVariant,
				modifier = Modifier.padding(top = 4.dp),
			)
		}
	}
}

/** A field's label: sentence-case mono, the one label style for every input. */
@Composable
fun HdFieldLabel(
	text: String,
	modifier: Modifier = Modifier,
	color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
	Text(text = text, style = hdMetaStyle(), color = color, modifier = modifier)
}
