package com.darkrockstudios.apps.hammer.common.projecthome

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
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
import com.github.skydoves.colorpicker.compose.AlphaSlider
import com.github.skydoves.colorpicker.compose.BrightnessSlider
import com.github.skydoves.colorpicker.compose.ColorEnvelope
import com.github.skydoves.colorpicker.compose.HsvColorPicker
import com.github.skydoves.colorpicker.compose.rememberColorPickerController

private const val DEFAULT_COLOR1 = "#FF455A64"
private const val DEFAULT_COLOR2 = "#FFFFB300"
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
	var color1 by remember(theme) { mutableStateOf(theme?.color1 ?: DEFAULT_COLOR1) }
	var color2 by remember(theme) { mutableStateOf(theme?.color2 ?: DEFAULT_COLOR2) }

	LaunchedEffect(enabled, color1, color2) {
		onChange(if (enabled) ProjectTheme(color1 = color1, color2 = color2) else null)
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
					label = Res.string.project_info_theme_color1_label.get(),
					hex = color1,
					onChange = { color1 = it },
					modifier = Modifier.weight(1f),
				)
				ColorSwatchPicker(
					label = Res.string.project_info_theme_color2_label.get(),
					hex = color2,
					onChange = { color2 = it },
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
	var currentHex by remember(visible) { mutableStateOf(toArgbHex(initial)) }

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

private fun parseHexColor(hex: String): Color? {
	val cleaned = hex.removePrefix("#")
	val (a, r, g, b) = when (cleaned.length) {
		6 -> arrayOf("FF", cleaned.substring(0, 2), cleaned.substring(2, 4), cleaned.substring(4, 6))
		8 -> arrayOf(cleaned.substring(0, 2), cleaned.substring(2, 4), cleaned.substring(4, 6), cleaned.substring(6, 8))
		else -> return null
	}
	return runCatching {
		Color(
			alpha = a.toInt(16) / 255f,
			red = r.toInt(16) / 255f,
			green = g.toInt(16) / 255f,
			blue = b.toInt(16) / 255f,
		)
	}.getOrNull()
}

private fun toArgbHex(color: Color): String {
	val a = (color.alpha * 255).toInt().coerceIn(0, 255)
	val r = (color.red * 255).toInt().coerceIn(0, 255)
	val g = (color.green * 255).toInt().coerceIn(0, 255)
	val b = (color.blue * 255).toInt().coerceIn(0, 255)
	return "#" + listOf(a, r, g, b).joinToString("") { it.toString(16).padStart(2, '0').uppercase() }
}
