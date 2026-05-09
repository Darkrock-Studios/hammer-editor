package com.darkrockstudios.apps.hammer.common.projecthome

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectTheme
import com.darkrockstudios.apps.hammer.base.http.projectdata.WordCountGoal
import com.darkrockstudios.apps.hammer.common.components.projecthome.ProjectSettings
import com.darkrockstudios.apps.hammer.common.compose.SimpleDialog
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineButton
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineField
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineSection
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMonoLabel
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.compose.theme.parseHexColor
import com.darkrockstudios.apps.hammer.common.compose.theme.toArgbHex
import com.github.skydoves.colorpicker.compose.*

private const val DEFAULT_PRIMARY = "#FF455A64"
private const val DEFAULT_SECONDARY = "#FFFFB300"
private const val DEFAULT_GOAL_COUNT = 500

@Composable
internal fun AuthorField(
	initial: String,
	onChange: (String?) -> Unit,
	modifier: Modifier = Modifier,
) {
	var text by remember(initial) { mutableStateOf(initial) }
	HdHairlineField(
		modifier = modifier,
		label = Res.string.project_info_author_label.get(),
		value = text,
		onValueChange = {
			text = it
			onChange(it.takeIf { v -> v.isNotBlank() })
		},
		imeAction = ImeAction.Done,
		capitalization = KeyboardCapitalization.Words,
	)
}

@Composable
internal fun CustomThemeSection(
	section: Int,
	theme: ProjectTheme?,
	onChange: (ProjectTheme?) -> Unit,
	modifier: Modifier = Modifier,
) {
	var enabled by remember(theme) { mutableStateOf(theme != null) }
	var primary by remember(theme) { mutableStateOf(theme?.primary ?: DEFAULT_PRIMARY) }
	var secondary by remember(theme) { mutableStateOf(theme?.secondary ?: DEFAULT_SECONDARY) }

	LaunchedEffect(enabled, primary, secondary) {
		onChange(if (enabled) ProjectTheme(primary = primary, secondary = secondary) else null)
	}

	HdHairlineSection(
		modifier = modifier,
		section = section,
		title = Res.string.project_info_theme_section_title.get(),
		headerTrailing = {
			HdMonoLabel(text = if (enabled) "ON" else "OFF · SYSTEM")
		},
		contentSpacing = 18.dp,
	) {
		HairlineToggleRow(
			checked = enabled,
			onCheckedChange = { enabled = it },
			label = Res.string.project_info_theme_enable_label.get(),
		)
		val gateAlpha = if (enabled) 1f else 0.45f
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.alpha(gateAlpha),
			verticalArrangement = Arrangement.spacedBy(14.dp),
		) {
			ColorRow(
				label = Res.string.project_info_theme_primary_label.get(),
				hex = primary,
				onChange = { if (enabled) primary = it },
			)
			ColorRow(
				label = Res.string.project_info_theme_secondary_label.get(),
				hex = secondary,
				onChange = { if (enabled) secondary = it },
			)
		}
	}
}

@Composable
internal fun WordCountGoalSection(
	section: Int,
	goal: WordCountGoal?,
	onChange: (WordCountGoal?) -> Unit,
	modifier: Modifier = Modifier,
) {
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

	val effectivePerDay = remember(count, cadence) {
		val n = count.toIntOrNull() ?: 0
		when (cadence) {
			WordCountGoal.Cadence.DAY -> n
			WordCountGoal.Cadence.WEEK -> if (n > 0) (n + 6) / 7 else 0
		}
	}
	val meta = if (enabled) {
		val cadenceLabel = when (cadence) {
			WordCountGoal.Cadence.DAY -> Res.string.project_info_word_goal_cadence_day.get()
			WordCountGoal.Cadence.WEEK -> Res.string.project_info_word_goal_cadence_week.get()
		}
		"${count.ifBlank { "0" }} W / ${cadenceLabel.uppercase()}"
	} else "OFF"

	HdHairlineSection(
		modifier = modifier,
		section = section,
		title = Res.string.project_info_word_goal_section_title.get(),
		headerTrailing = { HdMonoLabel(text = meta) },
		contentSpacing = 18.dp,
	) {
		HairlineToggleRow(
			checked = enabled,
			onCheckedChange = { enabled = it },
			label = Res.string.project_info_word_goal_enable_label.get(),
		)
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.alpha(if (enabled) 1f else 0.45f),
			verticalArrangement = Arrangement.spacedBy(16.dp),
		) {
			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.spacedBy(28.dp),
				verticalAlignment = Alignment.Bottom,
			) {
				HdHairlineField(
					modifier = Modifier.widthIn(min = 140.dp, max = 220.dp),
					label = Res.string.project_info_word_goal_count_label.get(),
					value = count,
					onValueChange = { input -> if (enabled) count = input.filter(Char::isDigit) },
					counter = "W",
					imeAction = ImeAction.Done,
				)
				CadencePicker(
					selected = cadence,
					onSelect = { if (enabled) cadence = it },
					modifier = Modifier.weight(1f),
				)
			}
			EffectivePerDay(perDay = effectivePerDay)
		}
	}
}

@Composable
private fun HairlineToggleRow(
	checked: Boolean,
	onCheckedChange: (Boolean) -> Unit,
	label: String,
	modifier: Modifier = Modifier,
	hint: String? = null,
) {
	Row(
		modifier = modifier
			.fillMaxWidth()
			.clickable { onCheckedChange(!checked) },
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(Ui.Padding.M),
	) {
		HairlineCheckbox(checked = checked)
		Column(modifier = Modifier.weight(1f)) {
			Text(
				text = label,
				style = MaterialTheme.typography.bodyLarge,
				color = MaterialTheme.colorScheme.onSurface,
			)
			if (hint != null) {
				Text(
					text = hint,
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
				)
			}
		}
	}
}

@Composable
private fun HairlineCheckbox(checked: Boolean) {
	val borderColor = if (checked) MaterialTheme.colorScheme.primary
	else MaterialTheme.colorScheme.outlineVariant
	val fill = if (checked) MaterialTheme.colorScheme.primary
	else Color.Transparent
	Box(
		modifier = Modifier
			.size(18.dp)
			.border(width = Dp.Hairline, color = borderColor, shape = RectangleShape)
			.background(fill, RectangleShape),
		contentAlignment = Alignment.Center,
	) {
		if (checked) {
			Text(
				text = "✓",
				style = MaterialTheme.typography.labelSmall,
				color = MaterialTheme.colorScheme.onPrimary,
			)
		}
	}
}

@Composable
private fun CadencePicker(
	selected: WordCountGoal.Cadence,
	onSelect: (WordCountGoal.Cadence) -> Unit,
	modifier: Modifier = Modifier,
) {
	Column(
		modifier = modifier,
		verticalArrangement = Arrangement.spacedBy(8.dp),
	) {
		HdMonoLabel(text = Res.string.project_info_word_goal_cadence_label.get())
		Row(modifier = Modifier.fillMaxWidth()) {
			CadenceCell(
				label = Res.string.project_info_word_goal_cadence_day.get(),
				selected = selected == WordCountGoal.Cadence.DAY,
				onClick = { onSelect(WordCountGoal.Cadence.DAY) },
				modifier = Modifier.weight(1f),
			)
			CadenceCell(
				label = Res.string.project_info_word_goal_cadence_week.get(),
				selected = selected == WordCountGoal.Cadence.WEEK,
				onClick = { onSelect(WordCountGoal.Cadence.WEEK) },
				modifier = Modifier.weight(1f).offset(x = (-1).dp),
			)
		}
	}
}

@Composable
private fun CadenceCell(
	label: String,
	selected: Boolean,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val borderColor = if (selected) MaterialTheme.colorScheme.onSurface
	else MaterialTheme.colorScheme.outlineVariant
	val labelColor = if (selected) MaterialTheme.colorScheme.onSurface
	else MaterialTheme.colorScheme.onSurfaceVariant
	Box(
		modifier = modifier
			.heightIn(min = 36.dp)
			.border(width = Dp.Hairline, color = borderColor, shape = RectangleShape)
			.clickable(onClick = onClick)
			.padding(horizontal = Ui.Padding.M, vertical = 6.dp),
		contentAlignment = Alignment.Center,
	) {
		Text(
			text = label.uppercase(),
			style = MaterialTheme.typography.labelMedium,
			color = labelColor,
		)
	}
}

@Composable
private fun EffectivePerDay(perDay: Int) {
	Row(
		modifier = Modifier.fillMaxWidth(),
		horizontalArrangement = Arrangement.spacedBy(Ui.Padding.M),
		verticalAlignment = Alignment.Bottom,
	) {
		HdMonoLabel(text = Res.string.project_info_word_goal_effective_label.get())
		Text(
			text = perDay.toString(),
			style = MaterialTheme.typography.headlineMedium,
			color = MaterialTheme.colorScheme.onSurface,
		)
		HdMonoLabel(text = Res.string.project_info_word_goal_effective_unit.get())
	}
}

@Composable
private fun ColorRow(
	label: String,
	hex: String,
	onChange: (String) -> Unit,
	modifier: Modifier = Modifier,
) {
	var pickerOpen by remember { mutableStateOf(false) }
	val color = parseHexColor(hex) ?: MaterialTheme.colorScheme.surfaceVariant

	Row(
		modifier = modifier
			.fillMaxWidth()
			.clickable { pickerOpen = true }
			.padding(vertical = 4.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(Ui.Padding.L),
	) {
		Box(
			modifier = Modifier
				.size(34.dp)
				.background(color, RectangleShape)
				.border(
					width = Dp.Hairline,
					color = MaterialTheme.colorScheme.outline,
					shape = RectangleShape,
				),
		)
		Column(modifier = Modifier.weight(1f)) {
			HdMonoLabel(text = label)
			Text(
				text = hex.uppercase(),
				style = MaterialTheme.typography.titleMedium,
				color = MaterialTheme.colorScheme.onSurface,
			)
		}
		HdMonoLabel(text = Res.string.project_info_color_picker_edit.get())
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
						.background(parseHexColor(currentHex) ?: initial, RectangleShape)
						.border(
							width = Dp.Hairline,
							color = MaterialTheme.colorScheme.outline,
							shape = RectangleShape,
						),
				)
				Spacer(Modifier.width(Ui.Padding.M))
				Text(
					currentHex,
					style = MaterialTheme.typography.bodyLarge,
					color = MaterialTheme.colorScheme.onSurface,
				)
			}

			Spacer(Modifier.size(Ui.Padding.L))

			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.spacedBy(Ui.Padding.M, Alignment.End),
			) {
				HdHairlineButton(
					label = Res.string.project_info_color_picker_cancel.get(),
					onClick = onDismiss,
				)
				HdHairlineButton(
					label = Res.string.project_info_color_picker_confirm.get(),
					onClick = { onConfirm(currentHex) },
					emphasised = true,
				)
			}
		}
	}
}

@Composable
fun ProjectInfoSettingsUi(component: ProjectSettings) {
	val state by component.projectInfoState.subscribeAsState()
	if (!state.isLoaded) return

	Column(verticalArrangement = Arrangement.spacedBy(40.dp)) {
		AuthorField(
			initial = state.data.authorName.orEmpty(),
			onChange = component::setAuthorName,
		)
		CustomThemeSection(
			section = 1,
			theme = state.data.theme,
			onChange = component::setTheme,
		)
		WordCountGoalSection(
			section = 2,
			goal = state.data.wordCountGoal,
			onChange = component::setWordCountGoal,
		)
	}
}
