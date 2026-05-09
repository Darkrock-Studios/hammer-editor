package com.darkrockstudios.apps.hammer.common.projectselection.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.common.components.spellchecksettings.SpellCheckSettings
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMonoLabel
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdSectionHeader
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.compose.theme.LocalHammerColors
import kotlinx.coroutines.launch

@Composable
internal fun SpellCheckSettingsUi(
	component: SpellCheckSettings,
) {
	Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
		HdSectionHeader(
			marker = "—",
			title = Res.string.settings_spellcheck_heading.get(),
		)
		SpellCheckSettingsContent(component)
	}
}

@Composable
internal fun SpellCheckSettingsContent(
	component: SpellCheckSettings,
) {
	val state by component.state.subscribeAsState()
	val scope = rememberCoroutineScope()

	Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
		ExperimentalNotice(text = Res.string.settings_spellcheck_notice.get())
		HairlineCheck(
			checked = state.spellCheckingEnabled,
			label = Res.string.settings_spellcheck_enable.get(),
			onCheckedChange = { scope.launch { component.setSpellcheckEnable(it) } },
		)
		HairlineCheck(
			checked = state.spellCheckingInFocusEnabled,
			enabled = state.spellCheckingEnabled,
			label = Res.string.settings_spellcheck_in_focus_enable.get(),
			onCheckedChange = { scope.launch { component.setSpellCheckingInFocusEnabled(it) } },
		)
		Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
			HdMonoLabel(text = Res.string.settings_spellcheck_dictionary.get())
			Text(
				text = state.spellCheckingLanguage.toString(),
				style = MaterialTheme.typography.titleMedium,
				color = MaterialTheme.colorScheme.onSurface,
			)
		}
	}
}

@Composable
private fun HairlineCheck(
	checked: Boolean,
	label: String,
	onCheckedChange: (Boolean) -> Unit,
	enabled: Boolean = true,
) {
	val borderColor = when {
		!enabled -> MaterialTheme.colorScheme.outlineVariant
		checked -> MaterialTheme.colorScheme.primary
		else -> MaterialTheme.colorScheme.outlineVariant
	}
	val fill = if (checked && enabled) MaterialTheme.colorScheme.primary else Color.Transparent
	val labelColor = if (enabled) MaterialTheme.colorScheme.onSurface
	else MaterialTheme.colorScheme.onSurfaceVariant

	Row(
		modifier = Modifier
			.fillMaxWidth()
			.let { if (enabled) it.clickable { onCheckedChange(!checked) } else it },
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(Ui.Padding.M),
	) {
		Box(
			modifier = Modifier
				.size(18.dp)
				.border(width = Dp.Hairline, color = borderColor, shape = RectangleShape)
				.background(fill, RectangleShape),
			contentAlignment = Alignment.Center,
		) {
			if (checked && enabled) {
				Text(
					text = "✓",
					style = MaterialTheme.typography.labelSmall,
					color = MaterialTheme.colorScheme.onPrimary,
				)
			}
		}
		Text(
			text = label,
			style = MaterialTheme.typography.bodyLarge,
			color = labelColor,
		)
	}
}

@Composable
private fun ExperimentalNotice(text: String) {
	val warning = LocalHammerColors.current.warning
	val labelTypography = MaterialTheme.typography.labelSmall
	val bodyTypography = MaterialTheme.typography.bodySmall
	val onSurface = MaterialTheme.colorScheme.onSurface

	val label = Res.string.notice_experimental_label.get()
	val notice = remember(text) {
		buildAnnotatedString {
			withStyle(
				SpanStyle(
					color = warning,
					fontSize = labelTypography.fontSize,
				),
			) {
				append("■   ")
			}
			withStyle(
				SpanStyle(
					fontFamily = labelTypography.fontFamily,
					fontWeight = labelTypography.fontWeight,
					fontSize = labelTypography.fontSize,
					letterSpacing = labelTypography.letterSpacing,
					color = warning,
				),
			) {
				append(label.uppercase())
			}
			withStyle(SpanStyle(fontSize = bodyTypography.fontSize)) {
				append("    ")
			}
			withStyle(
				SpanStyle(
					fontStyle = FontStyle.Italic,
					color = onSurface,
				),
			) {
				append(text)
			}
		}
	}

	Box(
		modifier = Modifier
			.fillMaxWidth()
			.border(
				width = Dp.Hairline,
				color = MaterialTheme.colorScheme.outlineVariant,
				shape = RectangleShape,
			)
			.padding(horizontal = Ui.Padding.M, vertical = Ui.Padding.S),
	) {
		Text(
			text = notice,
			style = bodyTypography.copy(
				lineHeight = bodyTypography.fontSize,
				lineHeightStyle = LineHeightStyle(
					alignment = LineHeightStyle.Alignment.Center,
					trim = LineHeightStyle.Trim.Both,
				),
			),
		)
	}
}
