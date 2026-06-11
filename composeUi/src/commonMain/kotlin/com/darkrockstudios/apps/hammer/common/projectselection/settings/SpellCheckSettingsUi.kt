package com.darkrockstudios.apps.hammer.common.projectselection.settings

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.components.spellchecksettings.SpellCheckSettings
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineToggleRow
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.compose.theme.LocalHammerColors
import com.darkrockstudios.apps.hammer.notice_experimental_label
import com.darkrockstudios.apps.hammer.settings_spellcheck_enable
import com.darkrockstudios.apps.hammer.settings_spellcheck_in_focus_enable
import com.darkrockstudios.apps.hammer.settings_spellcheck_notice
import kotlinx.coroutines.launch

@Composable
internal fun SpellCheckSettingsContent(
	component: SpellCheckSettings,
) {
	val state by component.state.subscribeAsState()
	val scope = rememberCoroutineScope()

	Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
		ExperimentalNotice(text = Res.string.settings_spellcheck_notice.get())
		HdHairlineToggleRow(
			checked = state.spellCheckingEnabled,
			label = Res.string.settings_spellcheck_enable.get(),
			onCheckedChange = { scope.launch { component.setSpellcheckEnable(it) } },
		)
		Box(
			modifier = Modifier.alpha(if (state.spellCheckingEnabled) 1f else 0.45f),
		) {
			HdHairlineToggleRow(
				checked = state.spellCheckingInFocusEnabled,
				label = Res.string.settings_spellcheck_in_focus_enable.get(),
				onCheckedChange = {
					if (state.spellCheckingEnabled) {
						scope.launch { component.setSpellCheckingInFocusEnabled(it) }
					}
				},
			)
		}
		SpellCheckDictionaryControl(
			selected = state.spellCheckingLanguage,
			available = state.spellCheckLanguages,
			enabled = state.spellCheckingEnabled,
			onSelect = { scope.launch { component.setSpellCheckLanguage(it) } },
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
