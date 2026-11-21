package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** True inside an [HdSettingRow] that has stacked its control under the title; controls fill the width then. */
val LocalHdSettingRowStacked = compositionLocalOf { false }

/**
 * A setting as one row: title and optional one-line description on the left, its control on
 * the right. Lone actions ("Reinstall", "Change…") are rows, not sections. Below 420dp the
 * control drops under the text, and pickers there span the width ([LocalHdSettingRowStacked]).
 *
 *     Example project                                   [Reinstall]
 *     Install an example to see how features are used.
 */
@Composable
fun HdSettingRow(
	title: String,
	modifier: Modifier = Modifier,
	description: String? = null,
	/** A current value shown as [HdMetaText] under the title, such as a folder path. */
	meta: String? = null,
	/** Titles the row in the error colour, for an action that can't be undone. */
	destructive: Boolean = false,
	control: @Composable () -> Unit,
) {
	BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
		val text: @Composable () -> Unit = {
			Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
				Text(
					text = title,
					style = MaterialTheme.typography.bodyLarge,
					color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
				)
				if (meta != null) HdMetaText(text = meta)
				if (description != null) {
					Text(
						text = description,
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
					)
				}
			}
		}
		if (maxWidth < 420.dp) {
			Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
				text()
				CompositionLocalProvider(LocalHdSettingRowStacked provides true) { control() }
			}
		} else {
			Row(
				modifier = Modifier.fillMaxWidth(),
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(24.dp),
			) {
				Column(modifier = Modifier.weight(1f)) { text() }
				control()
			}
		}
	}
}

/**
 * A titled group of [HdSettingRow]s and toggle rows. One hairline sits under the title; rows
 * inside separate by space alone, so a long settings page doesn't pile up rules.
 */
@Composable
fun HdSettingGroup(
	title: String?,
	modifier: Modifier = Modifier,
	content: @Composable ColumnScope.() -> Unit,
) {
	Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
		if (title != null) {
			Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
				// A size step above row titles (bodyLarge), so the two levels never read as one.
				Text(text = title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
				HorizontalDivider(thickness = Dp.Hairline, color = MaterialTheme.colorScheme.outlineVariant)
			}
		}
		content()
	}
}
