package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import com.darkrockstudios.apps.hammer.common.compose.Ui

/**
 * `[ ✓ ]  Label` — clickable row with an [HdHairlineCheckbox] and a
 * label / optional hint. The default toggle pattern in the design
 * system; replaces M3 `Checkbox` + `Text` rows.
 */
@Composable
fun HdHairlineToggleRow(
	checked: Boolean,
	onCheckedChange: (Boolean) -> Unit,
	label: String,
	modifier: Modifier = Modifier,
	hint: String? = null,
	enabled: Boolean = true,
) {
	Row(
		modifier = modifier
			.fillMaxWidth()
			.alpha(if (enabled) 1f else 0.45f)
			.clickable(enabled = enabled) { onCheckedChange(!checked) },
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(Ui.Padding.M),
	) {
		HdHairlineCheckbox(checked = checked)
		Column(modifier = Modifier.weight(1f)) {
			Text(
				text = label,
				style = MaterialTheme.typography.bodyLarge,
				color = MaterialTheme.colorScheme.onSurface,
			)
			if (hint != null) {
				Text(
					text = hint,
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
				)
			}
		}
	}
}
