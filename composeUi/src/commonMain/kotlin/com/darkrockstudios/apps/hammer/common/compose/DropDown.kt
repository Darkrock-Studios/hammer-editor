package com.darkrockstudios.apps.hammer.common.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun <T> DropDown(
	modifier: Modifier = Modifier,
	items: List<T>,
	defaultIndex: Int = 0,
	onValueChanged: (T) -> Unit
) {
	var selectedIndex by remember { mutableStateOf(defaultIndex) }
	var categoriesExpanded by remember { mutableStateOf(false) }

	Box(
		modifier = Modifier.wrapContentSize(Alignment.TopStart)
			.background(Color.LightGray)
			.border(1.dp, MaterialTheme.colors.onBackground)
			.clickable(onClick = { categoriesExpanded = true })
	) {
		Text(
			items[selectedIndex].toString(),
			modifier = modifier,
		)
		Icon(
			Icons.Rounded.ArrowDropDown,
			"Drop Down Menu",
			modifier = Modifier.align(Alignment.CenterEnd)
		)
		DropdownMenu(
			expanded = categoriesExpanded,
			onDismissRequest = { categoriesExpanded = false },
		) {
			items.forEachIndexed { index, item ->
				DropdownMenuItem(onClick = {
					selectedIndex = index
					categoriesExpanded = false
					onValueChanged(items[index])
				}) {
					Text(text = item.toString())
				}
			}
		}
	}
}