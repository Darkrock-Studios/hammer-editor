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
	defaultItem: T?,
	label: String? = null,
	getText: ((T) -> String)? = null,
	modifier: Modifier = Modifier,
	noneOption: String? = null,
	onValueChanged: (T?) -> Unit
) {
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
	var selectedText by rememberSaveable { mutableStateOf(getItemText(defaultItem)) }

	ExposedDropdownMenuBox(
		expanded = isExpanded,
		onExpandedChange = { isExpanded = it },
		modifier = modifier,
	) {
		TextField(
			value = selectedText,
			onValueChange = {},
			readOnly = true,
			trailingIcon = {
				ExposedDropdownMenuDefaults.TrailingIcon(expanded = isExpanded)
			},
			label = {
				if (label != null) {
					Text(text = label)
				}
			},
			colors = ExposedDropdownMenuDefaults.textFieldColors(),
			modifier = Modifier.menuAnchor(),
		)

		ExposedDropdownMenu(
			expanded = isExpanded,
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
						selectedText = noneOption
						isExpanded = false
					}
				)
			}

			items.forEach { item ->
				val text = getItemText(item)

				DropdownMenuItem(
					modifier = Modifier,
					text = {
						Text(text = text)
					},
					onClick = {
						onValueChanged(item)
						selectedText = text
						isExpanded = false
					}
				)
			}
		}
	}
}