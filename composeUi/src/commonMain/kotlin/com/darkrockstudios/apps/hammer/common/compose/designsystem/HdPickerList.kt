package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import com.darkrockstudios.apps.hammer.common.compose.MpScrollBarList
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.scrollBarOverlay

/** Rows deeper than this share the same indent so long names keep room. */
private const val MAX_INDENT_DEPTH = 4

/**
 * Hairline-bordered list for picking from an indented tree inside a dialog.
 * The visible height is capped at [maxVisibleRows] rows derived from the text
 * size, so the row count holds steady across font scales; overflow scrolls.
 */
@Composable
fun HdPickerList(
	modifier: Modifier = Modifier,
	maxVisibleRows: Int = 6,
	emptyText: String? = null,
	content: LazyListScope.() -> Unit,
) {
	Column(
		modifier = modifier
			.fillMaxWidth()
			.border(
				width = Dp.Hairline,
				color = MaterialTheme.colorScheme.outlineVariant,
				shape = RectangleShape,
			),
	) {
		if (emptyText != null) {
			Text(
				text = emptyText,
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				modifier = Modifier.padding(Ui.Padding.XL),
			)
		} else {
			val lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
			val rowHeight = with(LocalDensity.current) {
				(if (lineHeight.isSpecified) lineHeight.toDp() else 20.dp) + (Ui.Padding.M * 2)
			}
			val listState = rememberLazyListState()
			Box {
				LazyColumn(
					state = listState,
					modifier = Modifier
						.heightIn(max = rowHeight * maxVisibleRows)
						.padding(end = Ui.Padding.S),
					content = content,
				)
				MpScrollBarList(
					modifier = scrollBarOverlay(),
					state = listState,
				)
			}
		}
	}
}

/**
 * One row of an [HdPickerList]: depth-based indent, optional leading icon,
 * ellipsized label, and a trailing slot (count label, checkbox, ...).
 * Interaction (clickable/toggleable), selection background, and test tags are
 * the caller's, passed through [modifier].
 */
@Composable
fun HdPickerRow(
	label: String,
	modifier: Modifier = Modifier,
	depth: Int = 0,
	icon: ImageVector? = null,
	trailing: @Composable RowScope.() -> Unit = {},
) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.then(modifier)
			.padding(
				start = Ui.Padding.L + (Ui.Padding.XL * depth.coerceAtMost(MAX_INDENT_DEPTH)),
				end = Ui.Padding.L,
				top = Ui.Padding.M,
				bottom = Ui.Padding.M,
			),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(Ui.Padding.M),
	) {
		if (icon != null) {
			Icon(
				imageVector = icon,
				contentDescription = null,
				tint = MaterialTheme.colorScheme.onSurfaceVariant,
				modifier = Modifier.size(16.dp),
			)
		}
		Text(
			text = label,
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurface,
			maxLines = 1,
			overflow = TextOverflow.Ellipsis,
			modifier = Modifier.weight(1f),
		)
		trailing()
	}
}
