package com.darkrockstudios.apps.hammer.common.compose

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> ExposedDropDown(
	items: List<T>,
	selectedItem: T?,
	label: String? = null,
	getText: @Composable ((T) -> String)? = null,
	modifier: Modifier = Modifier,
	noneOption: String? = null,
	enabled: Boolean = true,
	onValueChanged: (T?) -> Unit
) {
	@Composable
	fun getItemText(item: T?): String {
		return if (item != null) {
			if (getText != null) {
				getText(item)
			} else {
				item.toString()
			}
		} else {
			noneOption ?: ""
		}
	}

	var isExpanded by rememberSaveable { mutableStateOf(false) }
	val selectedText = getItemText(selectedItem)

	ExposedDropdownMenuBox(
		expanded = isExpanded,
		onExpandedChange = { if (enabled) isExpanded = it },
		modifier = Modifier,
	) {
		TextField(
			value = selectedText,
			onValueChange = {},
			readOnly = true,
			enabled = enabled,
			trailingIcon = {
				ExposedDropdownMenuDefaults.TrailingIcon(expanded = isExpanded)
			},
			label = {
				if (label != null) {
					Text(text = label)
				}
			},
			colors = ExposedDropdownMenuDefaults.textFieldColors(),
			modifier = modifier.menuAnchor(),
		)

		ExposedDropdownMenu(
			expanded = isExpanded && enabled,
			onDismissRequest = { isExpanded = false },
		) {
			if (noneOption != null) {
				DropdownMenuItem(
					modifier = Modifier.exposedDropdownSize(),
					text = {
						Text(text = noneOption)
					},
					onClick = {
						onValueChanged(null)
						isExpanded = false
					}
				)
			}

			items.forEach { item ->
				val text = getItemText(item)

				DropdownMenuItem(
					modifier = Modifier.exposedDropdownSize(),
					text = {
						Text(text = text)
					},
					onClick = {
						onValueChanged(item)
						isExpanded = false
					}
				)
			}
		}
	}
}
