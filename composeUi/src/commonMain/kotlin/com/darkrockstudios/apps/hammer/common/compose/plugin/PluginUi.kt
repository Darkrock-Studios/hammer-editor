package com.darkrockstudios.apps.hammer.common.compose.plugin

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import com.darkrockstudios.apps.hammer.operations.OperationRegistry
import org.jetbrains.compose.resources.StringResource

/** The user-facing half of a plugin, paired with its `ClientPlugin` by [id]. */
interface PluginUi {
	val id: String
	val name: StringResource

	/** Display names for the export formats this plugin contributes, keyed by format id. */
	fun exportFormatLabels(): Map<String, StringResource> = emptyMap()

	/** Localized text for the plugin's declared settings, keyed by setting key. */
	fun settingLabels(): Map<String, SettingLabels> = emptyMap()

	/** Shown under the plugin's name in the Plugins section of Settings. Null for no pane. */
	val settingsPane: (@Composable ColumnScope.() -> Unit)? get() = null

	/** Added to the project home's menu. */
	fun projectActions(): List<ProjectAction> = emptyList()
}

/** A menu item on a project's home screen that runs work on the project, then says it is [done]. */
class ProjectAction(
	val label: StringResource,
	val done: StringResource,
	/** Runs off the main thread, with the project's name and the operations to work through. */
	val run: suspend (project: String, operations: OperationRegistry) -> Unit,
)
