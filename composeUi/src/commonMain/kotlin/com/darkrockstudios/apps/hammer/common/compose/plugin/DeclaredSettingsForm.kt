package com.darkrockstudios.apps.hammer.common.compose.plugin

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineDropdown
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineField
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineToggleRow
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.composeui.resources.*
import com.darkrockstudios.apps.hammer.operations.plugin.SettingDeclaration
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** A plugin's declared settings as a form, one field per declaration in order. */
@Composable
fun ColumnScope.DeclaredSettingsForm(
	declarations: List<SettingDeclaration>,
	values: JsonObject,
	onChange: (key: String, value: JsonPrimitive) -> Unit,
) {
	declarations.forEach { setting ->
		val label = setting.label
		val hint = setting.hint
		val value = values[setting.key]?.jsonPrimitive ?: setting.default
		when (setting) {
			is SettingDeclaration.Toggle -> HdHairlineToggleRow(
				checked = value.booleanOrNull ?: setting.defaultValue,
				onCheckedChange = { onChange(setting.key, JsonPrimitive(it)) },
				label = label,
				hint = hint,
			)

			is SettingDeclaration.Choice -> HdHairlineDropdown(
				title = label,
				options = setting.options,
				selected = setting.options.first { it.value == value.content },
				onSelect = { onChange(setting.key, JsonPrimitive(it.value)) },
				label = { option -> option.label },
			)

			is SettingDeclaration.Number -> NumberField(setting, label, hint, value.longOrNull ?: setting.defaultValue) {
				onChange(setting.key, JsonPrimitive(it))
			}

			is SettingDeclaration.Text -> HdHairlineField(
				label = label,
				value = value.content,
				onValueChange = { onChange(setting.key, JsonPrimitive(it)) },
				hint = hint,
				singleLine = !setting.multiline,
			)
		}
	}
}

/** Keeps what was typed while it is not yet a valid number, and only reports valid ones. */
@Composable
private fun NumberField(
	setting: SettingDeclaration.Number,
	label: String,
	hint: String?,
	value: Long,
	onChange: (Long) -> Unit,
) {
	var typed by remember(value) { mutableStateOf(value.toString()) }
	val parsed = typed.toLongOrNull()?.let { setting.accept(JsonPrimitive(it))?.longOrNull }
	HdHairlineField(
		label = label,
		value = typed,
		onValueChange = { entered ->
			typed = entered
			entered.toLongOrNull()?.let { number -> setting.accept(JsonPrimitive(number))?.longOrNull?.let(onChange) }
		},
		hint = hint,
		error = if (parsed == null) rangeText(setting) else null,
		keyboardType = KeyboardType.Number,
	)
}

@Composable
private fun rangeText(setting: SettingDeclaration.Number): String {
	val min = setting.min
	val max = setting.max
	return when {
		min != null && max != null -> Res.string.plugin_setting_range.get(min, max)
		min != null -> Res.string.plugin_setting_min.get(min)
		max != null -> Res.string.plugin_setting_max.get(max)
		else -> Res.string.plugin_setting_whole_number.get()
	}
}
