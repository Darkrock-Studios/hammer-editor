package com.darkrockstudios.apps.hammer.operations.plugin

import com.darkrockstudios.apps.hammer.common.data.export.StoryExporter
import com.darkrockstudios.apps.hammer.operations.cli.CliCommand

/**
 * A plugin active in this process, as the [PluginRegistry] sees it. Runtime plugins are the only
 * kind; everything a plugin adds is looked up while the app runs, so plugins can come and go.
 */
interface ClientPlugin {
	/** Stable, lowercase, directory-safe. Keys this plugin's settings file. */
	val id: String

	/** Shown to the user. */
	val name: String? get() = null

	/** Export formats this plugin adds. Each format id must start with `<id>.`, e.g. `smf.docx`. */
	fun exporters(): List<StoryExporter> = emptyList()

	/** Typed settings the host renders as a form and stores in the plugin's settings file. */
	fun settings(): List<SettingDeclaration> = emptyList()

	/** Extra top-level CLI commands, such as `hammer mcp`. Desktop only; ignored elsewhere. */
	fun cliCommands(): List<CliCommand> = emptyList()

	/** Items added to a project's menu. */
	fun projectActions(): List<ProjectAction> = emptyList()
}

/** A menu item on a project's home screen. */
class ProjectAction(
	val label: String,
	/** Whether [run] returns a markdown document to show in a dialog, rather than a short message. */
	val document: Boolean = false,
	/** Runs off the main thread on the named project; returns what to show the user, if anything. */
	val run: suspend (project: String) -> String?,
)
