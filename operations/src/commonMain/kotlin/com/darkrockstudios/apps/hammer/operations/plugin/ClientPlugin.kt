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

	/** Items added to the menus of a project's screens. */
	fun actions(): List<PluginAction> = emptyList()

	/** Checks the editor runs over the text being written, such as grammar, and underlines what they find. */
	fun textDiagnostics(): List<TextDiagnosticsProvider> = emptyList()
}

/** Finds issues in paragraphs of prose, for the editor to underline. */
class TextDiagnosticsProvider(
	val label: String,
	/**
	 * Whether it needs every paragraph of the scene each time, blank ones included, as when an issue
	 * depends on other paragraphs. Otherwise it gets only the paragraphs whose text it has not checked.
	 */
	val wholeScene: Boolean = false,
	/**
	 * The issues in each of the request's paragraphs, in the same order. Runs off the main thread, and
	 * returns nothing for paragraphs it cannot check rather than throwing.
	 */
	val diagnose: suspend (TextDiagnosticsRequest) -> List<List<TextDiagnostic>>,
)

class TextDiagnosticsRequest(
	/** Plain text, without markdown. */
	val paragraphs: List<String>,
	/** The project's BCP 47 tag, or null. */
	val language: String?,
	/** The name of the project the text belongs to. */
	val project: String,
)

/** An issue in one paragraph, from [start] to [end] as UTF-16 offsets into it. */
class TextDiagnostic(
	val start: Int,
	val end: Int,
	val message: String,
	/** Offered to the user. */
	val fixes: List<TextFix> = emptyList(),
	val severity: TextDiagnosticSeverity = TextDiagnosticSeverity.Error,
)

enum class TextDiagnosticSeverity {
	/** A mistake. */
	Error,

	/** Something the writer may want to change, marked more quietly. */
	Suggestion,
}

/** [replacement] for a [TextDiagnostic]'s range, shown to the user as [label]. */
class TextFix(
	val replacement: String,
	val label: String = replacement,
)
