package com.darkrockstudios.apps.hammer.common.compose

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.input.VisualTransformation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> ExposedDropDown(
	items: List<T>,
	selectedItem: T?,
	modifier: Modifier = Modifier,
	label: String? = null,
	getText: @Composable ((T) -> String)? = null,
	noneOption: String? = null,
	enabled: Boolean = true,
	expandWidth: Boolean = false,
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
	val interactionSource = remember { MutableInteractionSource() }

	ExposedDropdownMenuBox(
		expanded = isExpanded,
		onExpandedChange = { if (enabled) isExpanded = it },
		modifier = modifier.pointerHoverIcon(PointerIcon.Default),
	) {
		BasicTextField(
			value = selectedText,
			onValueChange = {},
			readOnly = true,
			enabled = enabled,
			singleLine = true,
			interactionSource = interactionSource,
			modifier = Modifier
				.then(if (expandWidth) Modifier.fillMaxWidth() else Modifier)
				.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
				.pointerHoverIcon(PointerIcon.Default),
		) { innerTextField ->
			val colors = ExposedDropdownMenuDefaults.textFieldColors()
			val textColor = if (enabled) colors.focusedTextColor else colors.disabledTextColor
			TextFieldDefaults.DecorationBox(
				value = selectedText,
				innerTextField = {
					Box {
						Text(
							selectedText,
							color = textColor,
							modifier = Modifier.pointerHoverIcon(PointerIcon.Default)
						)
					}
				},
				enabled = enabled,
				singleLine = true,
				visualTransformation = VisualTransformation.None,
				interactionSource = interactionSource,
				trailingIcon = {
					ExposedDropdownMenuDefaults.TrailingIcon(
						expanded = isExpanded,
						modifier = Modifier.pointerHoverIcon(PointerIcon.Default)
					)
				},
				label = if (label != null) {
					{ Text(text = label, modifier = Modifier.pointerHoverIcon(PointerIcon.Default)) }
				} else null,
				colors = colors,
			)
		}

		ExposedDropdownMenu(
			expanded = isExpanded && enabled,
			onDismissRequest = { isExpanded = false },
		) {
			if (noneOption != null) {
				DropdownMenuItem(
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
