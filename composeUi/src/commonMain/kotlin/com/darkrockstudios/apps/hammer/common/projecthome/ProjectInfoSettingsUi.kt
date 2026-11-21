package com.darkrockstudios.apps.hammer.common.projecthome

import com.darkrockstudios.apps.hammer.project_info_color_picker_title
import com.darkrockstudios.apps.hammer.common.compose.AnimatedDialog
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.BorderStroke
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineButton
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMetaText
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdSettingGroup
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdSettingRow
import com.darkrockstudios.apps.hammer.common.compose.designsystem.LocalHdSettingRowStacked
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.width
import com.darkrockstudios.apps.hammer.project_settings_edit_ellipsis
import com.darkrockstudios.apps.hammer.project_settings_enter_to_add_tag
import com.darkrockstudios.apps.hammer.project_settings_goal_per_day
import com.darkrockstudios.apps.hammer.project_settings_goal_row
import com.darkrockstudios.apps.hammer.project_settings_goal_words_per
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectTheme
import com.darkrockstudios.apps.hammer.base.http.projectdata.WordCountGoal
import com.darkrockstudios.apps.hammer.common.components.projecthome.ProjectSettings
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineButton
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineField
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineSegmentedPicker
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineTagField
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineToggleRow
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMonoLabel
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdSearchableListDialog
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.compose.theme.parseHexColor
import com.darkrockstudios.apps.hammer.common.compose.theme.toArgbHex
import com.darkrockstudios.apps.hammer.project_info_author_label
import com.darkrockstudios.apps.hammer.project_info_color_picker_cancel
import com.darkrockstudios.apps.hammer.project_info_color_picker_confirm
import com.darkrockstudios.apps.hammer.project_info_language_dialog_close
import com.darkrockstudios.apps.hammer.project_info_language_dialog_title
import com.darkrockstudios.apps.hammer.project_info_language_empty
import com.darkrockstudios.apps.hammer.project_info_language_label
import com.darkrockstudios.apps.hammer.project_info_language_search_hint
import com.darkrockstudios.apps.hammer.project_info_language_unset
import com.darkrockstudios.apps.hammer.project_info_tags_placeholder
import com.darkrockstudios.apps.hammer.project_info_tags_section_title
import com.darkrockstudios.apps.hammer.project_info_theme_enable_label
import com.darkrockstudios.apps.hammer.project_info_theme_primary_label
import com.darkrockstudios.apps.hammer.project_info_theme_secondary_label
import com.darkrockstudios.apps.hammer.project_info_theme_section_title
import com.darkrockstudios.apps.hammer.project_info_word_goal_cadence_day
import com.darkrockstudios.apps.hammer.project_info_word_goal_cadence_week
import com.darkrockstudios.apps.hammer.project_info_word_goal_enable_label
import com.darkrockstudios.apps.hammer.project_info_word_goal_section_title
import com.github.skydoves.colorpicker.compose.BrightnessSlider
import com.github.skydoves.colorpicker.compose.ColorEnvelope
import com.github.skydoves.colorpicker.compose.HsvColorPicker
import com.github.skydoves.colorpicker.compose.rememberColorPickerController

private const val DEFAULT_PRIMARY = "#FF455A64"
private const val DEFAULT_SECONDARY = "#FFFFB300"
private const val DEFAULT_GOAL_COUNT = 500

@Composable
internal fun AuthorField(
	initial: String,
	onChange: (String?) -> Unit,
	modifier: Modifier = Modifier,
	label: String = Res.string.project_info_author_label.get(),
) {
	var text by remember(initial) { mutableStateOf(initial) }
	HdHairlineField(
		modifier = modifier,
		label = label,
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
internal fun LanguageField(
	languageTag: String?,
	options: List<ProjectSettings.LanguageOption>,
	onChange: (String?) -> Unit,
	modifier: Modifier = Modifier,
	showLabel: Boolean = true,
) {
	var showPicker by remember { mutableStateOf(false) }
	val selected = options.firstOrNull { it.tag == languageTag }
	val displayName = selected?.displayName ?: languageTag

	Column(
		modifier = modifier,
		verticalArrangement = Arrangement.spacedBy(8.dp),
	) {
		if (showLabel) HdMonoLabel(text = Res.string.project_info_language_label.get())
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.heightIn(min = 36.dp)
				.border(
					width = Dp.Hairline,
					color = MaterialTheme.colorScheme.outlineVariant,
					shape = RectangleShape,
				)
				.clickable { showPicker = true }
				.padding(horizontal = Ui.Padding.M, vertical = 6.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(Ui.Padding.S),
		) {
			Text(
				text = displayName ?: Res.string.project_info_language_unset.get(),
				style = MaterialTheme.typography.bodyMedium,
				color = if (displayName != null) {
					MaterialTheme.colorScheme.onSurface
				} else {
					MaterialTheme.colorScheme.onSurfaceVariant
				},
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
				modifier = Modifier.weight(1f),
			)
			if (languageTag != null) {
				HdMetaText(text = languageTag)
			}
			Text(text = "▾", color = MaterialTheme.colorScheme.onSurfaceVariant)
		}
	}

	HdSearchableListDialog(
		visible = showPicker,
		title = Res.string.project_info_language_dialog_title.get(),
		items = options,
		itemLabel = { it.displayName },
		itemTrailing = { it.tag.uppercase() },
		itemKey = { it.tag },
		onSelect = { onChange(it.tag) },
		onDismiss = { showPicker = false },
		closeContentDescription = Res.string.project_info_language_dialog_close.get(),
		searchPlaceholder = Res.string.project_info_language_search_hint.get(),
		clearLabel = Res.string.project_info_language_unset.get(),
		onClear = { onChange(null) },
		emptyLabel = Res.string.project_info_language_empty.get(),
		isSelected = { it.tag == languageTag },
		clearSelected = languageTag == null,
	)
}

/** A row control's width beside its title; the full row when stacked under it. */
@Composable
private fun rowControlWidth(): Modifier =
	if (LocalHdSettingRowStacked.current) Modifier.fillMaxWidth() else Modifier.width(260.dp)

/**
 * The project's own facts, under its name: the one place the name shows on the screen.
 * [extra] adds rows such as the read-only sync status.
 */
@Composable
internal fun ProjectDetailsGroup(
	component: ProjectSettings,
	projectName: String,
	extra: @Composable ColumnScope.() -> Unit = {},
) {
	val state by component.projectInfoState.subscribeAsState()
	if (!state.isLoaded) return
	HdSettingGroup(title = projectName) {
		HdSettingRow(title = Res.string.project_info_author_label.get()) {
			AuthorField(
				initial = state.data.authorName.orEmpty(),
				onChange = component::setAuthorName,
				label = "",
				modifier = rowControlWidth(),
			)
		}
		HdSettingRow(title = Res.string.project_info_language_label.get()) {
			LanguageField(
				languageTag = state.data.language,
				options = component.availableLanguages,
				onChange = component::setProjectLanguage,
				showLabel = false,
				modifier = rowControlWidth(),
			)
		}
		extra()
	}
}

@Composable
internal fun CustomThemeGroup(
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

	HdSettingGroup(title = Res.string.project_info_theme_section_title.get(), modifier = modifier) {
		HdHairlineToggleRow(
			checked = enabled,
			onCheckedChange = { enabled = it },
			label = Res.string.project_info_theme_enable_label.get(),
		)
		// The colours only matter with the theme on, so they appear with it rather than dimmed.
		if (enabled) {
			ColorRow(
				label = Res.string.project_info_theme_primary_label.get(),
				hex = primary,
				onChange = { primary = it },
			)
			ColorRow(
				label = Res.string.project_info_theme_secondary_label.get(),
				hex = secondary,
				onChange = { secondary = it },
			)
		}
	}
}

@Composable
internal fun WordCountGoalGroup(
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

	HdSettingGroup(title = Res.string.project_info_word_goal_section_title.get(), modifier = modifier) {
		HdHairlineToggleRow(
			checked = enabled,
			onCheckedChange = { enabled = it },
			label = Res.string.project_info_word_goal_enable_label.get(),
		)
		if (enabled) {
			// Only a weekly goal says something new as a daily figure.
			val perDay = count.toIntOrNull()?.takeIf { it > 0 && cadence == WordCountGoal.Cadence.WEEK }?.let { (it + 6) / 7 }
			HdSettingRow(
				title = Res.string.project_settings_goal_row.get(),
				description = perDay?.let { Res.string.project_settings_goal_per_day.get(it) },
			) {
				Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
					HdHairlineField(
						modifier = Modifier.width(90.dp),
						label = "",
						value = count,
						onValueChange = { input -> count = input.filter(Char::isDigit) },
						imeAction = ImeAction.Done,
						keyboardType = KeyboardType.Number,
					)
					Text(
						text = Res.string.project_settings_goal_words_per.get(),
						style = MaterialTheme.typography.bodyMedium,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
					)
					HdHairlineSegmentedPicker(
						options = WordCountGoal.Cadence.entries,
						selected = cadence,
						onSelect = { cadence = it },
						label = { c ->
							when (c) {
								WordCountGoal.Cadence.DAY -> Res.string.project_info_word_goal_cadence_day.get()
								WordCountGoal.Cadence.WEEK -> Res.string.project_info_word_goal_cadence_week.get()
							}
						},
						compact = true,
					)
				}
			}
		}
	}
}

@Composable
internal fun ProjectTagsGroup(
	tags: Set<String>,
	onChange: (Set<String>) -> Unit,
	suggest: (prefix: String) -> List<String>,
	modifier: Modifier = Modifier,
) {
	var localTags by remember(tags) { mutableStateOf(tags.toList()) }

	HdSettingGroup(title = Res.string.project_info_tags_section_title.get(), modifier = modifier) {
		Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
			HdHairlineTagField(
				label = "",
				tags = localTags,
				onTagsChange = { updated ->
					// Same rule the component persists with: a chip that won't survive
					// setTags must not render, or it lingers as a phantom all session.
					val cleaned = ProjectSettings.cleanProjectTags(updated.toSet())
					localTags = updated.filter { it in cleaned }
					onChange(cleaned)
				},
				placeholder = Res.string.project_info_tags_placeholder.get(),
				suggestTags = suggest,
				testTag = "project-settings-tags",
			)
			Text(
				text = Res.string.project_settings_enter_to_add_tag.get(),
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)
		}
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

	Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
		Box(
			modifier = Modifier
				.padding(end = Ui.Padding.L)
				.size(34.dp)
				.background(color, RectangleShape)
				.border(width = Dp.Hairline, color = MaterialTheme.colorScheme.outline, shape = RectangleShape),
		)
		HdSettingRow(title = label, meta = hex.uppercase(), modifier = Modifier.weight(1f)) {
			HdHairlineButton(label = Res.string.project_settings_edit_ellipsis.get(), onClick = { pickerOpen = true })
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
	AnimatedDialog(visible = visible, onCloseRequest = onDismiss, dismissOnTapOutside = true) {
		ColorPickerCard(
			title = title,
			initial = initial,
			onDismiss = { requestDismiss() },
			onConfirm = onConfirm,
		)
	}
}

/** The picker without its dialog window: wheel, brightness, the old color beside the new, and the actions. */
@Composable
internal fun ColorPickerCard(title: String, initial: Color, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
	val controller = rememberColorPickerController()
	var currentHex by remember { mutableStateOf(initial.toArgbHex()) }

	Surface(
		modifier = Modifier.padding(Ui.Padding.L).width(340.dp),
		shape = RectangleShape,
		color = MaterialTheme.colorScheme.surface,
		border = BorderStroke(Dp.Hairline, MaterialTheme.colorScheme.outlineVariant),
	) {
		Column(
			modifier = Modifier.padding(Ui.Padding.XL),
			verticalArrangement = Arrangement.spacedBy(Ui.Padding.M),
		) {
			Text(
				text = Res.string.project_info_color_picker_title.get(title),
				style = MaterialTheme.typography.titleLarge,
			)
			HsvColorPicker(
				modifier = Modifier
					.fillMaxWidth()
					.height(220.dp),
				controller = controller,
				initialColor = initial,
				onColorChanged = { envelope: ColorEnvelope ->
					currentHex = "#FF${envelope.hexCode.takeLast(6).uppercase()}"
				},
			)
			BrightnessSlider(
				modifier = Modifier
					.fillMaxWidth()
					.height(24.dp),
				controller = controller,
			)
			Row(verticalAlignment = Alignment.CenterVertically) {
				Row(
					modifier = Modifier
						.size(width = 64.dp, height = 32.dp)
						.border(Dp.Hairline, MaterialTheme.colorScheme.outline, RectangleShape),
				) {
					Box(Modifier.weight(1f).fillMaxHeight().background(initial))
					Box(Modifier.weight(1f).fillMaxHeight().background(parseHexColor(currentHex) ?: initial))
				}
				Spacer(Modifier.width(Ui.Padding.M))
				HdMonoLabel(
					text = "#" + currentHex.takeLast(6),
					color = MaterialTheme.colorScheme.onSurface,
				)
			}
			Row(
				modifier = Modifier.fillMaxWidth().padding(top = Ui.Padding.S),
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
