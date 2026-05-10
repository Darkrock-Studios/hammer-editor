package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Hairline-underline labeled text field — the create-entry vocabulary.
 *
 * Above the input: a small-caps [HdMonoLabel] with optional [hint] on
 * the left (e.g. "Markdown") and an optional [counter] on the right
 * (e.g. "0/60"). The input is a chrome-less [BasicTextField] sitting on
 * the surface; below it is a hairline rule that reads as the field's
 * underline.
 *
 *     NAME                                  0/60
 *     Cheshire Cat
 *     ────────────────────────────────────────────
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
	capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
) {
	Column(modifier = modifier.fillMaxWidth()) {
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
			Box(modifier = Modifier.weight(1f))
			if (counter != null) {
				HdMonoLabel(
					text = counter,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
				)
			}
		}
		val onSurface = MaterialTheme.colorScheme.onSurface
		val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant
		val errorColor = MaterialTheme.colorScheme.error
		val textStyle = (if (singleLine) MaterialTheme.typography.bodyLarge
		else MaterialTheme.typography.bodyMedium)
			.copy(color = onSurface)

		Box(
			modifier = Modifier
				.fillMaxWidth()
				.padding(top = 6.dp, bottom = 6.dp),
		) {
			BasicTextField(
				value = value,
				onValueChange = onValueChange,
				modifier = Modifier.fillMaxWidth(),
				singleLine = singleLine,
				minLines = minLines,
				maxLines = maxLines,
				textStyle = textStyle,
				cursorBrush = SolidColor(onSurface),
				keyboardOptions = KeyboardOptions(
					imeAction = imeAction,
					capitalization = capitalization,
				),
			)
			if (value.isEmpty() && placeholder != null) {
				Text(
					text = placeholder,
					style = textStyle.copy(color = mutedColor),
				)
			}
		}
		HorizontalDivider(
			thickness = Dp.Hairline,
			color = if (error != null) errorColor
			else MaterialTheme.colorScheme.outlineVariant,
		)
		if (error != null) {
			HdMonoLabel(
				modifier = Modifier.padding(top = 4.dp),
				text = error,
				color = errorColor,
			)
		}
	}
}
