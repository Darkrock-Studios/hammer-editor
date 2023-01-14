package com.darkrockstudios.apps.hammer.common.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
			.background(Color.LightGray)
			.border(1.dp, MaterialTheme.colors.onBackground)
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
			).wrapContentWidth()
		)
		Icon(
			Icons.Rounded.ArrowDropDown,
			"Drop Down Menu",
			modifier = Modifier.align(Alignment.CenterEnd)
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