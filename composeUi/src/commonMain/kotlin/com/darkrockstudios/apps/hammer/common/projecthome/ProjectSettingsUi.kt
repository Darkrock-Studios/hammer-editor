package com.darkrockstudios.apps.hammer.common.projecthome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.components.projecthome.ProjectSettings
import com.darkrockstudios.apps.hammer.common.components.spellchecksettings.SpellCheckSettings
import com.darkrockstudios.apps.hammer.common.compose.LocalScreenCharacteristic
import com.darkrockstudios.apps.hammer.common.compose.MpScrollBarColumn
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdCrumbBackLink
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdFolioDivider
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineSection
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineToggleRow
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMonoLabel
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.compose.scrollBarOverlay
import com.darkrockstudios.apps.hammer.common.compose.theme.LocalHammerColors
import com.darkrockstudios.apps.hammer.common.projectselection.settings.SpellCheckSettingsContent
import com.darkrockstudios.apps.hammer.common.spellcheck.displayName
import com.darkrockstudios.apps.hammer.common.spellcheck.isSpellCheckAllowedForProject
import com.darkrockstudios.apps.hammer.common.util.Locale
import com.darkrockstudios.apps.hammer.project_settings_autosaved
import com.darkrockstudios.apps.hammer.project_settings_breadcrumb_home
import com.darkrockstudios.apps.hammer.project_settings_breadcrumb_root
import com.darkrockstudios.apps.hammer.project_settings_folio_caption
import com.darkrockstudios.apps.hammer.project_settings_folio_section_count
import com.darkrockstudios.apps.hammer.project_settings_hero_by
import com.darkrockstudios.apps.hammer.project_settings_hero_marker
import com.darkrockstudios.apps.hammer.project_settings_hero_no_author
import com.darkrockstudios.apps.hammer.project_settings_spellcheck_encyclopedia_enable
import com.darkrockstudios.apps.hammer.project_settings_spellcheck_encyclopedia_enable_hint
import com.darkrockstudios.apps.hammer.project_settings_spellcheck_mismatch_caption
import com.darkrockstudios.apps.hammer.project_settings_spellcheck_section_title

private val MaxColumnWidth = 880.dp

@Composable
fun ProjectSettingsUi(
	modifier: Modifier,
	component: ProjectSettings,
	onClose: () -> Unit,
) {
	val state by component.projectInfoState.subscribeAsState()
	val screen = LocalScreenCharacteristic.current
	val isCompact = screen.windowWidthClass == WindowWidthSizeClass.Compact

	val outerHorizontal: Dp = if (isCompact) Ui.Padding.XL else 56.dp
	val outerVertical: Dp = if (isCompact) Ui.Padding.L else 28.dp

	Column(
		modifier = modifier
			.fillMaxSize()
			.background(MaterialTheme.colorScheme.surface),
	) {
		Breadcrumb(
			projectName = component.projectName,
			isCompact = isCompact,
			onClose = onClose,
		)
		HdFolioDivider()

		val scrollState = rememberScrollState()
		Box(
			modifier = Modifier
				.weight(1f)
				.fillMaxWidth(),
		) {
			Column(
				modifier = Modifier
					.fillMaxSize()
					.verticalScroll(scrollState)
					.padding(horizontal = outerHorizontal, vertical = outerVertical),
			) {
				Box(
					modifier = Modifier
						.widthIn(max = MaxColumnWidth)
						.fillMaxWidth()
						.align(Alignment.CenterHorizontally),
				) {
					Column(verticalArrangement = Arrangement.spacedBy(64.dp)) {
						Hero(
							projectName = component.projectName,
							authorName = state.data.authorName,
							isCompact = isCompact,
						)

						if (state.isLoaded) {
							ProjectInfoSettingsUi(component)

							HdHairlineSection(
								section = 4,
								title = Res.string.project_settings_spellcheck_section_title.get(),
								contentSpacing = 18.dp,
							) {
								SpellCheckSettingsContent(component.spellCheckSettings)
								ProjectDictionaryToggle(
									enabled = state.data.encyclopediaDictionary,
									component = component,
								)
								SpellCheckMismatchCaption(
									projectLanguageTag = state.data.language,
									spellCheckSettings = component.spellCheckSettings,
								)
							}
						}

						Spacer(Modifier.height(8.dp))
					}
				}
			}

			MpScrollBarColumn(
				modifier = scrollBarOverlay(),
				state = scrollState,
			)
		}

		FolioCaption(
			projectName = component.projectName,
			sectionCount = 4,
			horizontalPadding = outerHorizontal,
		)
	}
}

/** Per-project override for the global "learn encyclopedia names" feature. */
@Composable
private fun ProjectDictionaryToggle(
	enabled: Boolean,
	component: ProjectSettings,
) {
	val spellCheckState by component.spellCheckSettings.state.subscribeAsState()
	val featureOn = spellCheckState.spellCheckingEnabled && spellCheckState.spellCheckingEncyclopediaEnabled

	Box(
		modifier = Modifier.alpha(if (featureOn) 1f else 0.45f),
	) {
		HdHairlineToggleRow(
			checked = enabled,
			label = Res.string.project_settings_spellcheck_encyclopedia_enable.get(),
			hint = Res.string.project_settings_spellcheck_encyclopedia_enable_hint.get(),
			onCheckedChange = {
				if (featureOn) {
					component.setEncyclopediaDictionaryEnabled(it)
				}
			},
		)
	}
}

/** Explains why spell check isn't running when the project's language gates it off. */
@Composable
private fun SpellCheckMismatchCaption(
	projectLanguageTag: String?,
	spellCheckSettings: SpellCheckSettings,
) {
	val spellCheckState by spellCheckSettings.state.subscribeAsState()
	if (!spellCheckState.spellCheckingEnabled) return
	if (projectLanguageTag == null) return
	if (isSpellCheckAllowedForProject(projectLanguageTag, spellCheckState.spellCheckingLanguage)) return

	Text(
		text = Res.string.project_settings_spellcheck_mismatch_caption.get(
			Locale.forLanguageTag(projectLanguageTag).displayName(),
			spellCheckState.spellCheckingLanguage.displayName(),
		),
		style = MaterialTheme.typography.bodySmall,
		color = MaterialTheme.colorScheme.onSurfaceVariant,
	)
}

@Composable
private fun Breadcrumb(
	projectName: String,
	isCompact: Boolean,
	onClose: () -> Unit,
) {
	val horizontal: Dp = if (isCompact) Ui.Padding.XL else 56.dp
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = horizontal, vertical = 14.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(12.dp),
	) {
		HdCrumbBackLink(
			label = Res.string.project_settings_breadcrumb_home.get(),
			onClick = onClose,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
		HdMonoLabel(
			text = "/",
			color = MaterialTheme.colorScheme.outlineVariant,
		)
		HdMonoLabel(
			text = Res.string.project_settings_breadcrumb_root.get(),
			color = MaterialTheme.colorScheme.onSurface,
		)
		if (!isCompact) {
			HdMonoLabel(
				text = "/",
				color = MaterialTheme.colorScheme.outlineVariant,
			)
			HdMonoLabel(
				modifier = Modifier.weight(1f, fill = false),
				text = projectName,
				color = MaterialTheme.colorScheme.onSurface,
			)
		}
		Spacer(Modifier.weight(1f))
		AutosaveBadge()
	}
}

@Composable
private fun AutosaveBadge() {
	val success = LocalHammerColors.current.success
	Row(
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(8.dp),
	) {
		Box(
			modifier = Modifier
				.size(6.dp)
				.background(success, RectangleShape),
		)
		HdMonoLabel(
			text = Res.string.project_settings_autosaved.get(),
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
	}
}

@Composable
private fun Hero(
	projectName: String,
	authorName: String?,
	isCompact: Boolean,
) {
	Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
		HdMonoLabel(text = Res.string.project_settings_hero_marker.get())
		Text(
			text = projectName,
			style = if (isCompact) MaterialTheme.typography.displaySmall
			else MaterialTheme.typography.displayMedium,
			color = MaterialTheme.colorScheme.onSurface,
			maxLines = 2,
			overflow = TextOverflow.Ellipsis,
		)
		val by = if (authorName.isNullOrBlank()) {
			Res.string.project_settings_hero_no_author.get()
		} else {
			Res.string.project_settings_hero_by.get(authorName)
		}
		Text(
			text = by,
			style = MaterialTheme.typography.bodyLarge,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
	}
}

@Composable
private fun FolioCaption(
	projectName: String,
	sectionCount: Int,
	horizontalPadding: Dp,
) {
	HorizontalDivider(
		thickness = Dp.Hairline,
		color = MaterialTheme.colorScheme.outlineVariant,
	)
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = horizontalPadding, vertical = 10.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
	) {
		HdMonoLabel(
			text = Res.string.project_settings_folio_caption.get(projectName),
		)
		HdMonoLabel(
			text = "·",
			color = MaterialTheme.colorScheme.outlineVariant,
		)
		HdMonoLabel(
			text = Res.string.project_settings_folio_section_count.get(sectionCount),
		)
	}
}

