package com.darkrockstudios.apps.hammer.common.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun <T> DropDown(
	modifier: Modifier = Modifier,
	padding: Dp = 16.dp,
	items: List<T>,
	defaultIndex: Int = 0,
	noneOption: String? = null,
	onValueChanged: (T?) -> Unit
) {
	var selectedIndex by remember { mutableStateOf(defaultIndex) }
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
			items[index].toString()
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
				DropdownMenuItem(onClick = {
					selectedIndex = 0
					itemsExpanded = false
					onValueChanged(null)
				}) {
					Text(noneOption)
				}
			}

			items.forEachIndexed { index, item ->
				DropdownMenuItem(onClick = {
					selectedIndex = index + indexOffset
					itemsExpanded = false
					onValueChanged(items[index])
				}) {
					Text(text = item.toString())
				}
			}
		}
	}
}