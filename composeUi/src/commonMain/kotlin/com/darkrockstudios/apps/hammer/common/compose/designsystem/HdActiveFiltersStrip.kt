package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.compose.Ui

/**
 * The active-filter summary strip under a [HdTagFilterBar]: a hairline rule, the hit summary,
 * one removable [HdTagChip] per active tag, and a clear-all affordance. Show it only while a
 * filter is active (callers typically wrap it in `AnimatedVisibility`).
 *
 *     ────────────────────────────────────────
 *     FILTERED 3 / 12   # gothic ×  # coastal ×   CLEAR ALL
 *
 * [filteredLabel] arrives pre-formatted (`"FILTERED 3 / 12"` — real values, per the greeble
 * rule) so the component stays string-resource free.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HdActiveFiltersStrip(
	activeTags: Set<String>,
	filteredLabel: String,
	clearAllLabel: String,
	onToggle: (String) -> Unit,
	onClear: () -> Unit,
	modifier: Modifier = Modifier,
) {
	Column(modifier = modifier.fillMaxWidth()) {
		HorizontalDivider(
			thickness = Dp.Hairline,
			color = MaterialTheme.colorScheme.outlineVariant,
		)
		FlowRow(
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = Ui.Padding.XL, vertical = Ui.Padding.S),
			horizontalArrangement = Arrangement.spacedBy(6.dp),
			verticalArrangement = Arrangement.spacedBy(6.dp),
		) {
			HdMonoLabel(
				text = filteredLabel,
				modifier = Modifier
					.padding(end = Ui.Padding.S)
					.align(Alignment.CenterVertically),
			)
			activeTags.sorted().forEach { tag ->
				HdTagChip(
					label = tag,
					active = true,
					onClick = { onToggle(tag) },
					onRemove = { onToggle(tag) },
				)
			}
			Box(
				modifier = Modifier
					.clickable(onClick = onClear)
					.padding(horizontal = Ui.Padding.S, vertical = Ui.Padding.S),
				contentAlignment = Alignment.Center,
			) {
				HdMonoLabel(text = clearAllLabel)
			}
		}
	}
}
