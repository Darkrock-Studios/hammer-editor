package com.darkrockstudios.apps.hammer.common.compose.designsystem

import com.darkrockstudios.apps.hammer.common.compose.actions.MenuOverlay
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.compose.Ui

/** One entry in an [HdSplitButton]'s menu. */
class HdSplitButtonItem(val label: String, val onClick: () -> Unit)

/**
 * The primary action with an attached ▾ for related, rarer ones ("New project ▾" → New
 * group). Replaces a primary button plus separate icon buttons for sibling actions.
 * Filled in `primary`, like every colored button. A [stretch] button takes its width from
 * [modifier] (e.g. `weight(1f)` in a bottom bar); otherwise it wraps.
 */
@Composable
fun HdSplitButton(
	label: String,
	onClick: () -> Unit,
	menuItems: List<HdSplitButtonItem>,
	menuContentDescription: String,
	modifier: Modifier = Modifier,
	mainModifier: Modifier = Modifier,
	menuModifier: Modifier = Modifier,
	stretch: Boolean = false,
	height: Dp = 32.dp,
) {
	var expanded by remember { mutableStateOf(false) }
	val fill = MaterialTheme.colorScheme.primary
	val border = fill
	val content = MaterialTheme.colorScheme.onPrimary
	Row(
		modifier = modifier
			.height(height)
			.then(if (stretch) Modifier else Modifier.width(IntrinsicSize.Max))
			.background(fill)
			.border(Dp.Hairline, border, RectangleShape),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Box(
			modifier = Modifier
				.weight(1f)
				.fillMaxHeight()
				.then(mainModifier)
				.clickable(role = Role.Button, onClick = onClick)
				.padding(horizontal = Ui.Padding.L),
			contentAlignment = Alignment.Center,
		) {
			Text(
				text = label,
				style = MaterialTheme.typography.labelLarge,
				fontWeight = FontWeight.Medium,
				color = content,
				maxLines = 1,
				softWrap = false,
			)
		}
		// A hairline would vanish on the fill; the seam has to read as a second button.
		Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(content.copy(alpha = 0.4f)))
		Box {
			Box(
				modifier = Modifier
					.fillMaxHeight()
					.then(menuModifier)
					.clickable(role = Role.Button, onClick = { expanded = true })
					.padding(horizontal = 6.dp),
				contentAlignment = Alignment.Center,
			) {
				Icon(Icons.Filled.ArrowDropDown, contentDescription = menuContentDescription, tint = content)
			}
			MenuOverlay(expanded) { expanded = false }
			DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
				menuItems.forEach { item ->
					DropdownMenuItem(
						text = { Text(item.label) },
						onClick = {
							expanded = false
							item.onClick()
						},
					)
				}
			}
		}
	}
}
