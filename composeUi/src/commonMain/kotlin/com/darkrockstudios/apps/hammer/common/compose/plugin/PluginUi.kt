package com.darkrockstudios.apps.hammer.common.compose.plugin

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.StringResource

/** The user-facing half of a plugin, paired with its `ClientPlugin` by [id]. */
interface PluginUi {
	val id: String
	val name: StringResource

	/** Shown under the plugin's name in the Plugins section of Settings. Null for no pane. */
	val settingsPane: (@Composable ColumnScope.() -> Unit)? get() = null
}
