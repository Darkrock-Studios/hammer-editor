package com.darkrockstudios.apps.hammer.common.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.darkrockstudios.apps.hammer.common.compose.plugin.DeclaredSettingsForm
import com.darkrockstudios.apps.hammer.common.compose.plugin.PluginSettingsPane
import com.darkrockstudios.apps.hammer.common.projectselection.settings.ManagePluginsCard
import com.darkrockstudios.apps.hammer.common.projectselection.settings.PluginSettingsCard
import com.darkrockstudios.apps.hammer.operations.plugin.SettingDeclaration
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** The manage plugins dialog over a scrim, listing the same plugins as the Plugins tab preview. */
@Composable
internal fun ManagePluginsScrimPreview() {
	Scrimmed {
		ManagePluginsCard(onClose = {}) { RuntimePluginsListPreview(showHeader = false) }
	}
}

/** A grammar plugin's declared settings in their dialog, over a scrim. */
@Composable
internal fun PluginSettingsScrimPreview() {
	var values by remember {
		mutableStateOf(JsonObject(mapOf("language" to JsonPrimitive("en-US"), "server" to JsonPrimitive("https://lt.example.com"))))
	}
	Scrimmed {
		PluginSettingsCard(
			pane = PluginSettingsPane(pluginId = "grammar", name = "Grammar check") {
				DeclaredSettingsForm(
					declarations = previewDeclarations,
					values = values,
					onChange = { key, value -> values = JsonObject(values + (key to value)) },
				)
			},
			onClose = {},
		)
	}
}

@Composable
private fun Scrimmed(content: @Composable () -> Unit) {
	Box(
		modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f)),
		contentAlignment = Alignment.Center,
	) {
		content()
	}
}

private val previewDeclarations = listOf(
	SettingDeclaration.Choice(
		key = "language",
		label = "Language",
		defaultValue = "en-US",
		options = listOf(
			SettingDeclaration.Choice.Option("en-US", "English (US)"),
			SettingDeclaration.Choice.Option("en-GB", "English (UK)"),
		),
	),
	SettingDeclaration.Toggle(
		key = "picky",
		label = "Picky mode",
		defaultValue = false,
		hint = "Also flags style issues, such as wordiness and passive voice.",
	),
	SettingDeclaration.Toggle(
		key = "own_server",
		label = "Use my own server",
		defaultValue = true,
		danger = true,
	),
	SettingDeclaration.Text(key = "server", label = "Server address", hint = "Where your LanguageTool server runs."),
	SettingDeclaration.Secret(key = "api_key", label = "API key"),
	SettingDeclaration.Number(key = "max_issues", label = "Most issues per paragraph", defaultValue = 10, min = 1, max = 50),
)
