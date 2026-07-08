package com.darkrockstudios.apps.hammer.common.preview.projecthome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectData
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectTheme
import com.darkrockstudios.apps.hammer.base.http.projectdata.WordCountGoal
import com.darkrockstudios.apps.hammer.common.components.projecthome.ProjectSettings
import com.darkrockstudios.apps.hammer.common.components.spellchecksettings.SpellCheckSettings
import com.darkrockstudios.apps.hammer.common.compose.theme.AppTheme
import com.darkrockstudios.apps.hammer.common.preview.KoinApplicationPreview
import com.darkrockstudios.apps.hammer.common.preview.TABLET_HEIGHT_DP
import com.darkrockstudios.apps.hammer.common.preview.TABLET_WIDTH_DP
import com.darkrockstudios.apps.hammer.common.preview.TabletPreviewSurface
import com.darkrockstudios.apps.hammer.common.preview.globalSettingsPreview
import com.darkrockstudios.apps.hammer.common.projecthome.ProjectSettingsUi
import com.darkrockstudios.apps.hammer.common.util.Locale

@Preview
@Composable
fun ScreenProjectSettingsUiPreview() {
	KoinApplicationPreview {
		AppTheme(globalSettingsPreview) {
			Box(
				modifier = Modifier
					.background(MaterialTheme.colorScheme.background)
					.fillMaxSize(),
			) {
				ProjectSettingsUi(
					modifier = Modifier,
					component = component,
					onClose = {},
				)
			}
		}
	}
}

@Preview(widthDp = TABLET_WIDTH_DP, heightDp = TABLET_HEIGHT_DP)
@Composable
fun ScreenProjectSettingsUiTabletPreview() {
	KoinApplicationPreview {
		TabletPreviewSurface {
			ProjectSettingsUi(
				modifier = Modifier,
				component = component,
				onClose = {},
			)
		}
	}
}

private val fakeSpellCheckSettings = object : SpellCheckSettings {
	override val state: Value<SpellCheckSettings.State> = MutableValue(
		SpellCheckSettings.State(
			spellCheckingEnabled = true,
			spellCheckingInFocusEnabled = false,
			spellCheckingLanguage = Locale.root,
			spellCheckLanguages = listOf(Locale.root),
		)
	)

	override suspend fun setSpellcheckEnable(enable: Boolean) {}
	override suspend fun setSpellCheckingInFocusEnabled(enable: Boolean) {}
	override suspend fun setSpellCheckLanguage(language: Locale) {}
}

private val component = object : ProjectSettings {
	override val projectName: String = "The Lighthouse"
	override val spellCheckSettings: SpellCheckSettings = fakeSpellCheckSettings
	override val projectInfoState: Value<ProjectSettings.ProjectInfoState> = MutableValue(
		ProjectSettings.ProjectInfoState(
			data = ProjectData(authorName = "A. Writer", tags = setOf("fantasy", "draft")),
			isLoaded = true,
		)
	)

	override fun setAuthorName(name: String?) {}
	override fun setTheme(theme: ProjectTheme?) {}
	override fun setWordCountGoal(goal: WordCountGoal?) {}
	override fun setTags(tags: Set<String>) {}
	override fun suggestProjectTags(prefix: String): List<String> =
		listOf("fantasy", "sci-fi", "nanowrimo").filter { it.startsWith(prefix, ignoreCase = true) }
}
