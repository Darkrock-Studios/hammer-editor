package com.darkrockstudios.apps.hammer.common.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun <T> ExposedDropDown(
	items: List<T>,
	getText: ((T) -> String)? = null,
	modifier: Modifier = Modifier,
	padding: Dp = 16.dp,
	defaultIndex: Int = 0,
	noneOption: String? = null,
	onValueChanged: (T?) -> Unit
) {
	var selectedIndex by remember(defaultIndex) { mutableStateOf(defaultIndex) }
	var itemsExpanded by remember { mutableStateOf(false) }

	BoxWithConstraints(
		modifier = modifier
			.background(MaterialTheme.colorScheme.tertiaryContainer)
			.border(1.dp, MaterialTheme.colorScheme.onTertiary)
			.clickable(onClick = { itemsExpanded = true })
	) {
		val itemText = if (selectedIndex == 0 && noneOption != null) {
			noneOption
		} else {
			val index = if (noneOption != null) {
				selectedIndex - 1
			} else {
				selectedIndex
			}

			val item = items[index]
			if (getText != null) {
				getText(item)
			} else {
				item.toString()
			}
		}

		val indexOffset = if (noneOption != null) {
			1
		} else {
			0
		}

		Text(
			itemText,
			modifier = Modifier.padding(
				PaddingValues(start = padding, end = padding * 2, top = padding, bottom = padding)
			).wrapContentWidth(),
			color = MaterialTheme.colorScheme.onTertiaryContainer
		)
		Icon(
			Icons.Rounded.ArrowDropDown,
			"Drop Down Menu",
			modifier = Modifier.align(Alignment.CenterEnd),
			tint = MaterialTheme.colorScheme.onTertiaryContainer
		)
		DropdownMenu(
			expanded = itemsExpanded,
			onDismissRequest = { itemsExpanded = false },
		) {
			if (noneOption != null) {
				DropdownMenuItem(
					onClick = {
						selectedIndex = 0
						itemsExpanded = false
						onValueChanged(null)
					},
					text = { Text(noneOption) }
				)
			}

			items.forEachIndexed { index, item ->
				val text = if (getText != null) {
					getText(item)
				} else {
					item.toString()
				}

				DropdownMenuItem(
					onClick = {
						selectedIndex = index + indexOffset
						itemsExpanded = false
						onValueChanged(items[index])
					},
					text = { Text(text = text) }
				)
			}
		}
	}
}