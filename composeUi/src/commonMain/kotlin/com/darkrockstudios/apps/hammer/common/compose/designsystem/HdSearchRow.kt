package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The revealed-search row: a weighted [HdSearchField] with a trailing [HdCollapseGlyph] that
 * tucks the row back into the chrome. Screens own the reveal state and decide whether collapsing
 * also clears the query via [onCollapse].
 *
 *     ┌─────────────────────────────────────────┐
 *     │ ⌕  filter by name              ×  │  ⤒
 *     └─────────────────────────────────────────┘
 */
@Composable
fun HdSearchRow(
	query: String,
	onQueryChange: (String) -> Unit,
	placeholder: String,
	clearContentDescription: String,
	onCollapse: () -> Unit,
	collapseContentDescription: String,
	modifier: Modifier = Modifier,
	testTag: String? = null,
	onClear: () -> Unit = { onQueryChange("") },
) {
	Row(
		modifier = modifier,
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(8.dp),
	) {
		HdSearchField(
			value = query,
			onValueChange = onQueryChange,
			placeholder = placeholder,
			onClear = onClear,
			clearContentDescription = clearContentDescription,
			modifier = Modifier.weight(1f),
			testTag = testTag,
		)
		HdCollapseGlyph(
			onClick = onCollapse,
			contentDescription = collapseContentDescription,
		)
	}
}
