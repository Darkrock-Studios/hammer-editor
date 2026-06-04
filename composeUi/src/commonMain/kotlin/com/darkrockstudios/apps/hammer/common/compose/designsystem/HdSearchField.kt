package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Hairline-bordered, square-cornered single-line search field. Sits in
 * the design-system vocabulary alongside [HdEntryFilterBar] and
 * [HdTypeStamp]: 32dp tall, mono placeholder, leading search icon, trailing
 * × clear affordance when [value] is non-empty.
 *
 *     ┌─────────────────────────────────────────────┐
 *     │ ⌕  search by name                         × │
 *     └─────────────────────────────────────────────┘
 */
@Composable
fun HdSearchField(
	value: String,
	onValueChange: (String) -> Unit,
	modifier: Modifier = Modifier,
	placeholder: String = "Search",
	onSearch: ((String) -> Unit)? = null,
	onClear: (() -> Unit)? = null,
	clearContentDescription: String? = null,
	focusRequester: FocusRequester? = null,
	testTag: String? = null,
) {
	val ruleColor = MaterialTheme.colorScheme.outlineVariant
	val onSurface = MaterialTheme.colorScheme.onSurface
	val onSurfaceMuted = MaterialTheme.colorScheme.onSurfaceVariant
	val mutedAccent = onSurfaceMuted.copy(alpha = 0.7f)

	Row(
		modifier = modifier
			.height(32.dp)
			.background(MaterialTheme.colorScheme.surface)
			.border(width = Dp.Hairline, color = ruleColor, shape = RectangleShape)
			.padding(horizontal = 12.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(8.dp),
	) {
		Icon(
			imageVector = Icons.Default.Search,
			contentDescription = null,
			tint = mutedAccent,
			modifier = Modifier.size(16.dp),
		)
		Box(modifier = Modifier.weight(1f)) {
			val textStyle = MaterialTheme.typography.labelMedium.copy(color = onSurface)
			BasicTextField(
				value = value,
				onValueChange = onValueChange,
				singleLine = true,
				textStyle = textStyle,
				cursorBrush = SolidColor(onSurface),
				keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
				keyboardActions = KeyboardActions(onSearch = { onSearch?.invoke(value) }),
				modifier = Modifier
					.fillMaxWidth()
					.then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
					.then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
			)
			if (value.isEmpty()) {
				Text(
					text = placeholder,
					style = textStyle.copy(color = mutedAccent),
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
				)
			}
		}
		if (value.isNotEmpty() && onClear != null) {
			Box(
				modifier = Modifier
					.size(20.dp)
					.clickable(onClick = onClear),
				contentAlignment = Alignment.Center,
			) {
				Text(
					text = "×",
					style = LocalTextStyle.current,
					color = onSurfaceMuted,
				)
			}
		}
	}
}
