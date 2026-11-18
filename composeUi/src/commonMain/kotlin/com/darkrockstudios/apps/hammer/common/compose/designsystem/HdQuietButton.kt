package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.compose.Ui

/**
 * The unframed button tier: an icon and/or a sentence-case label in `onSurfaceVariant`.
 * For masthead tools beside the one framed primary action. The icon names the kind of
 * control; the label gives its current value or action, never a restatement of the icon.
 */
@Composable
fun HdQuietButton(
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
	icon: ImageVector? = null,
	label: String? = null,
	contentDescription: String? = null,
	trailingIcon: ImageVector? = null,
	active: Boolean = false,
) {
	val color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
	Row(
		modifier = modifier
			.height(32.dp)
			.clickable(role = Role.Button, onClick = onClick)
			.padding(horizontal = if (label == null) 6.dp else Ui.Padding.M),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(6.dp),
	) {
		if (icon != null) {
			Icon(imageVector = icon, contentDescription = contentDescription, tint = color, modifier = Modifier.size(18.dp))
		}
		if (label != null) {
			Text(text = label, style = MaterialTheme.typography.labelLarge, color = color, maxLines = 1, softWrap = false)
		}
		if (trailingIcon != null) {
			Icon(imageVector = trailingIcon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
		}
	}
}
