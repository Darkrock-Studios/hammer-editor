package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.compose.SpacerM
import com.darkrockstudios.apps.hammer.common.compose.Ui

/**
 * One tab of a row of them in a dialog: a label over a rule, the rule in the accent when [active].
 * Give each the same weight so the row splits evenly.
 *
 *       Characters        Locations          Other
 *     ━━━━━━━━━━━━━━  ──────────────  ──────────────
 */
@Composable
fun HdTabPill(
	label: String,
	active: Boolean,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val accent = MaterialTheme.colorScheme.primary
	val onSurface = MaterialTheme.colorScheme.onSurface
	val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant
	Box(
		modifier = modifier
			.clickable(role = Role.Tab, onClick = onClick)
			.semantics { selected = active }
			.padding(vertical = Ui.Padding.M),
		contentAlignment = Alignment.Center,
	) {
		Column(horizontalAlignment = Alignment.CenterHorizontally) {
			Text(
				text = label,
				style = MaterialTheme.typography.labelLarge,
				color = if (active) onSurface else mutedColor,
			)
			SpacerM()
			Box(
				modifier = Modifier
					.fillMaxWidth()
					.height(2.dp)
					.background(if (active) accent else MaterialTheme.colorScheme.outlineVariant)
			)
		}
	}
}
