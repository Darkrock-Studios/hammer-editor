package com.darkrockstudios.apps.hammer.common.projecthome

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectTheme
import com.darkrockstudios.apps.hammer.base.http.projectdata.WordCountGoal
import com.darkrockstudios.apps.hammer.common.components.projecthome.ProjectSettings
import com.darkrockstudios.apps.hammer.common.compose.SimpleDialog
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.compose.theme.parseHexColor
import com.darkrockstudios.apps.hammer.common.compose.theme.toArgbHex
import com.github.skydoves.colorpicker.compose.*

private const val DEFAULT_PRIMARY = "#FF455A64"
private const val DEFAULT_SECONDARY = "#FFFFB300"
private const val DEFAULT_GOAL_COUNT = 500

@Composable
fun ProjectInfoSettingsUi(component: ProjectSettings) {
	val state by component.projectInfoState.subscribeAsState()
	if (!state.isLoaded) return

	Column(modifier = Modifier.padding(Ui.Padding.M)) {
		Text(
			Res.string.project_info_section_title.get(),
			style = MaterialTheme.typography.headlineSmall,
			color = MaterialTheme.colorScheme.onBackground,
		)

		Spacer(Modifier.size(Ui.Padding.M))

		AuthorNameField(
			initial = state.data.authorName.orEmpty(),
			onChange = component::setAuthorName,
		)
		Spacer(Modifier.size(Ui.Padding.M))

		ThemeSection(
			theme = state.data.theme,
			onChange = component::setTheme,
		)
		Spacer(Modifier.size(Ui.Padding.M))

		WordCountGoalSection(
			goal = state.data.wordCountGoal,
			onChange = component::setWordCountGoal,
		)
	}
}

@Composable
private fun AuthorNameField(initial: String, onChange: (String?) -> Unit) {
	var text by remember(initial) { mutableStateOf(initial) }
	OutlinedTextField(
		value = text,
		onValueChange = {
			text = it
			onChange(it.takeIf { v -> v.isNotBlank() })
		},
		label = { Text(Res.string.project_info_author_label.get()) },
		singleLine = true,
		modifier = Modifier.fillMaxWidth(),
	)
}

@Composable
private fun ThemeSection(theme: ProjectTheme?, onChange: (ProjectTheme?) -> Unit) {
	var enabled by remember(theme) { mutableStateOf(theme != null) }
	var primary by remember(theme) { mutableStateOf(theme?.primary ?: DEFAULT_PRIMARY) }
	var secondary by remember(theme) { mutableStateOf(theme?.secondary ?: DEFAULT_SECONDARY) }

	LaunchedEffect(enabled, primary, secondary) {
		onChange(if (enabled) ProjectTheme(primary = primary, secondary = secondary) else null)
	}

	Column {
		Row(verticalAlignment = Alignment.CenterVertically) {
			Checkbox(checked = enabled, onCheckedChange = { enabled = it })
			Text(
				Res.string.project_info_theme_enable_label.get(),
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onBackground,
				modifier = Modifier.align(Alignment.CenterVertically),
			)
		}
		if (enabled) {
			Spacer(Modifier.size(Ui.Padding.S))
			Row(
				modifier = Modifier.fillMaxWidth().padding(start = Ui.Padding.L),
				horizontalArrangement = Arrangement.spacedBy(Ui.Padding.L),
			) {
				ColorSwatchPicker(
					label = Res.string.project_info_theme_primary_label.get(),
					hex = primary,
					onChange = { primary = it },
					modifier = Modifier.weight(1f),
				)
				ColorSwatchPicker(
					label = Res.string.project_info_theme_secondary_label.get(),
					hex = secondary,
					onChange = { secondary = it },
					modifier = Modifier.weight(1f),
				)
			}
		}
	}
}

@Composable
private fun ColorSwatchPicker(
	label: String,
	hex: String,
	onChange: (String) -> Unit,
	modifier: Modifier = Modifier,
) {
	var pickerOpen by remember { mutableStateOf(false) }
	val color = parseHexColor(hex) ?: MaterialTheme.colorScheme.surfaceVariant

	Column(modifier = modifier) {
		Text(
			label,
			style = MaterialTheme.typography.labelMedium,
			color = MaterialTheme.colorScheme.onBackground,
		)
		Spacer(Modifier.size(Ui.Padding.S))
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.height(48.dp)
				.clip(RoundedCornerShape(8.dp))
				.border(
					1.dp,
					MaterialTheme.colorScheme.outline,
					RoundedCornerShape(8.dp),
				)
				.clickable { pickerOpen = true }
				.padding(horizontal = Ui.Padding.M),
			verticalAlignment = Alignment.CenterVertically,
		) {
			Box(
				modifier = Modifier
					.size(28.dp)
					.clip(RoundedCornerShape(4.dp))
					.background(color)
					.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp)),
			)
			Spacer(Modifier.width(Ui.Padding.M))
			Text(
				hex.uppercase(),
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onBackground,
			)
		}
	}

	ColorPickerDialog(
		visible = pickerOpen,
		title = label,
		initial = color,
		onDismiss = { pickerOpen = false },
		onConfirm = { picked ->
			onChange(picked)
			pickerOpen = false
		},
	)
}

@Composable
private fun ColorPickerDialog(
	visible: Boolean,
	title: String,
	initial: Color,
	onDismiss: () -> Unit,
	onConfirm: (String) -> Unit,
) {
	val controller = rememberColorPickerController()
	var currentHex by remember(visible) { mutableStateOf(initial.toArgbHex()) }

	SimpleDialog(
		onCloseRequest = onDismiss,
		visible = visible,
		title = title,
		dismissOnTapOutside = true,
		overridePlatformWidth = true,
		modifier = Modifier.width(320.dp),
	) {
		Column(modifier = Modifier.fillMaxWidth().padding(top = Ui.Padding.M)) {
			HsvColorPicker(
				modifier = Modifier
					.fillMaxWidth()
					.height(220.dp)
					.padding(bottom = Ui.Padding.M),
				controller = controller,
				initialColor = initial,
				onColorChanged = { envelope: ColorEnvelope ->
					currentHex = "#${envelope.hexCode.uppercase()}"
				},
			)
			BrightnessSlider(
				modifier = Modifier
					.fillMaxWidth()
					.height(28.dp)
					.padding(vertical = 4.dp),
				controller = controller,
			)
			AlphaSlider(
				modifier = Modifier
					.fillMaxWidth()
					.height(28.dp)
					.padding(vertical = 4.dp),
				controller = controller,
			)

			Spacer(Modifier.size(Ui.Padding.M))

			Row(verticalAlignment = Alignment.CenterVertically) {
				Box(
					modifier = Modifier
						.size(36.dp)
						.clip(RoundedCornerShape(4.dp))
						.background(parseHexColor(currentHex) ?: initial)
						.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp)),
				)
				Spacer(Modifier.width(Ui.Padding.M))
				Text(
					currentHex,
					style = MaterialTheme.typography.bodyLarge,
					color = MaterialTheme.colorScheme.onBackground,
				)
			}

			Spacer(Modifier.size(Ui.Padding.L))

			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.End,
			) {
				TextButton(onClick = onDismiss) {
					Text(Res.string.project_info_color_picker_cancel.get())
				}
				Spacer(Modifier.width(Ui.Padding.S))
				Button(onClick = { onConfirm(currentHex) }) {
					Text(Res.string.project_info_color_picker_confirm.get())
				}
			}
		}
	}
}

@Composable
private fun WordCountGoalSection(goal: WordCountGoal?, onChange: (WordCountGoal?) -> Unit) {
	var enabled by remember(goal) { mutableStateOf(goal != null) }
	var count by remember(goal) { mutableStateOf((goal?.count ?: DEFAULT_GOAL_COUNT).toString()) }
	var cadence by remember(goal) { mutableStateOf(goal?.cadence ?: WordCountGoal.Cadence.DAY) }

	LaunchedEffect(enabled, count, cadence) {
		if (enabled) {
			val parsed = count.toIntOrNull()?.takeIf { it > 0 }
			if (parsed != null) onChange(WordCountGoal(cadence = cadence, count = parsed))
		} else {
			onChange(null)
		}
	}

	Column {
		Row(verticalAlignment = Alignment.CenterVertically) {
			Checkbox(checked = enabled, onCheckedChange = { enabled = it })
			Text(
				Res.string.project_info_word_goal_enable_label.get(),
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onBackground,
				modifier = Modifier.align(Alignment.CenterVertically),
			)
		}
		if (enabled) {
			Spacer(Modifier.size(Ui.Padding.S))
			Row(
				modifier = Modifier.padding(start = Ui.Padding.L),
				verticalAlignment = Alignment.CenterVertically,
			) {
				OutlinedTextField(
					value = count,
					onValueChange = { input -> count = input.filter(Char::isDigit) },
					label = { Text(Res.string.project_info_word_goal_count_label.get()) },
					keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
					singleLine = true,
					modifier = Modifier.width(160.dp),
				)
				Spacer(Modifier.width(Ui.Padding.L))
				Column {
					Text(
						Res.string.project_info_word_goal_cadence_label.get(),
						style = MaterialTheme.typography.labelMedium,
						color = MaterialTheme.colorScheme.onBackground,
					)
					Row {
						CadenceOption(
							label = Res.string.project_info_word_goal_cadence_day.get(),
							selected = cadence == WordCountGoal.Cadence.DAY,
							onClick = { cadence = WordCountGoal.Cadence.DAY },
						)
						Spacer(Modifier.width(Ui.Padding.M))
						CadenceOption(
							label = Res.string.project_info_word_goal_cadence_week.get(),
							selected = cadence == WordCountGoal.Cadence.WEEK,
							onClick = { cadence = WordCountGoal.Cadence.WEEK },
						)
					}
				}
			}
		}
	}
}

@Composable
private fun CadenceOption(label: String, selected: Boolean, onClick: () -> Unit) {
	Row(
		modifier = Modifier
			.selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
			.padding(Ui.Padding.S),
		verticalAlignment = Alignment.CenterVertically,
	) {
		RadioButton(selected = selected, onClick = onClick)
		Spacer(Modifier.size(Ui.Padding.S))
		Text(
			label,
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onBackground,
		)
	}
}

