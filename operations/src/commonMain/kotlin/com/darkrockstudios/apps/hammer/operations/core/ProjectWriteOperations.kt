package com.darkrockstudios.apps.hammer.operations.core

import com.darkrockstudios.apps.hammer.common.components.projecthome.ImportStoryUseCase
import com.darkrockstudios.apps.hammer.common.data.ClientResult
import com.darkrockstudios.apps.hammer.common.data.ImportFormat
import com.darkrockstudios.apps.hammer.common.data.ImportOptions
import com.darkrockstudios.apps.hammer.common.data.MarkdownSplitStrategy
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.RtfSplitStrategy
import com.darkrockstudios.apps.hammer.common.data.importer.ImportPreview
import com.darkrockstudios.apps.hammer.common.data.importer.StoryImporterRegistry
import com.darkrockstudios.apps.hammer.common.data.isProjectOpen
import com.darkrockstudios.apps.hammer.common.data.projectbackup.ProjectBackupRepository
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectRenameFailed
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsService
import com.darkrockstudios.apps.hammer.operations.Access
import com.darkrockstudios.apps.hammer.operations.Base64Bytes
import com.darkrockstudios.apps.hammer.operations.Operation
import com.darkrockstudios.apps.hammer.operations.invalidInput
import com.darkrockstudios.apps.hammer.operations.operation
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

internal fun projectWriteOperations(): List<Operation<*, *>> = listOf(
	operation<ProjectCreateInput, ProjectName>(
		name = "project.create",
		description = "Create an empty project.",
		access = Access.Write,
		agentVisible = true,
	) { input ->
		ProjectName(createProject(input.name).name)
	},
	operation<ProjectRenameInput, ProjectName>(
		name = "project.rename",
		description = "Rename a project. It must not be open in Hammer.",
		access = Access.Write,
		agentVisible = true,
	) { input ->
		val def = requireClosed(projects.resolve(input.project))
		val newName = input.name.trim()
		when (val result = koinGet<ProjectsService>().renameProject(def, newName)) {
			is ClientResult.Success -> ProjectName(result.data.name)
			is ClientResult.Failure -> when ((result.exception as? ProjectRenameFailed)?.reason) {
				ProjectRenameFailed.Reason.AlreadyExists -> invalidInput("A project named '$newName' already exists")
				ProjectRenameFailed.Reason.InvalidName -> invalidInput("'$newName' is not a valid project name")
				else -> error("Could not rename '${def.name}' to '$newName'")
			}
		}
	},
	operation<ProjectInput, ProjectName>(
		name = "project.delete",
		description = "Delete a project, and everything in it, for good. It must not be open in Hammer.",
		access = Access.Destructive,
	) { input ->
		val def = requireClosed(projects.resolve(input.project))
		if (!koinGet<ProjectsService>().deleteProject(def)) error("Could not delete '${def.name}'")
		ProjectName(def.name)
	},
	operation<ProjectImportInput, ProjectImported>(
		name = "project.import",
		description = "Create a project from a Markdown or RTF manuscript, split into scenes by its headings or a chapter pattern.",
		access = Access.Write,
		agentVisible = true,
	) { input ->
		val preview = preview(input)
		if (preview.isEmpty) invalidInput("The file has nothing to import")
		val name = input.name
			?: preview.title?.let(ProjectsRepository::sanitizeFileName)?.takeIf { it.isNotBlank() }
			?: invalidInput("Give the project a name")
		val def = createProject(name)
		val scenes = try {
			projects.withProject(def.name) { it.scope.get<ImportStoryUseCase>().execute(preview) }
		} catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
			// A half-imported project would block a retry under the same name.
			koinGet<ProjectsService>().deleteProject(def)
			throw e
		}
		ProjectImported(def.name, scenes)
	},
	operation<ProjectInput, Backup>(
		name = "backup.create",
		description = "Back up a project's saved files.",
		access = Access.Write,
		agentVisible = true,
	) { input ->
		val def = projects.resolve(input.project)
		val backups = koinGet<ProjectBackupRepository>()
		if (!backups.supportsBackup()) invalidInput("Backups are not supported on this device")
		val backup = backups.createBackup(def) ?: error("Could not back up '${def.name}'")
		// Dated as backup.list dates it, by the file's modification time.
		Backup(backups.getBackups(def).find { it.path == backup.path }?.date ?: backup.date)
	},
)

private fun createProject(name: String): ProjectDef {
	if (koinGet<ProjectsRepository>().findProject(name.trim()) != null) invalidInput("A project named '${name.trim()}' already exists")
	return when (val result = koinGet<ProjectsService>().createProject(name)) {
		is ClientResult.Success -> result.data
		is ClientResult.Failure -> invalidInput("Could not create a project named '$name'")
	}
}

/**
 * Renaming or deleting a project moves its directory out from under anything that has it open. Only
 * a best effort inside the app, where a sync can open it just after; forwarding keeps these headless.
 */
private fun requireClosed(def: ProjectDef): ProjectDef {
	if (isProjectOpen(def)) invalidInput("'${def.name}' is open in Hammer. Close it and try again.")
	return def
}

// Importers hand off to third-party parsers that can throw on a malformed file.
@Suppress("TooGenericExceptionCaught")
private fun preview(input: ProjectImportInput): ImportPreview {
	val options = ImportOptions(
		format = input.format.format,
		markdownSplitStrategy = input.markdownSplit.strategy,
		createChapterGroups = input.chapterGroups,
		rtfSplitStrategy = input.rtfSplit.strategy,
	).let { options ->
		input.chapterPattern?.let { options.copy(markdownChapterPattern = it, rtfChapterPattern = it) } ?: options
	}
	return try {
		koinGet<StoryImporterRegistry>().forFormat(options.format).preview(input.name ?: UNTITLED, input.content, options)
	} catch (e: CancellationException) {
		throw e
	} catch (e: Exception) {
		invalidInput("Could not read the file: ${e.message}")
	}
}

private const val UNTITLED = "Imported"

@Serializable
data class ProjectCreateInput(val name: String)

@Serializable
data class ProjectRenameInput(val project: String, val name: String)

@Serializable
data class ProjectName(val name: String)

@Serializable
enum class ImportFileFormat(val format: ImportFormat) {
	@SerialName("markdown") Markdown(ImportFormat.Markdown),
	@SerialName("rtf") Rtf(ImportFormat.Rtf),
}

@Serializable
enum class MarkdownSplit(val strategy: MarkdownSplitStrategy) {
	/** Scenes at the heading level the document uses most. */
	@SerialName("auto") Auto(MarkdownSplitStrategy.Auto),
	@SerialName("h1") H1(MarkdownSplitStrategy.H1),
	@SerialName("h2") H2(MarkdownSplitStrategy.H2),

	/** Scenes at lines matching chapterPattern. */
	@SerialName("pattern") Pattern(MarkdownSplitStrategy.Pattern),
}

@Serializable
enum class RtfSplit(val strategy: RtfSplitStrategy) {
	/** Scenes at headings the document's formatting marks. */
	@SerialName("formatting") Formatting(RtfSplitStrategy.Formatting),

	/** Scenes at lines matching chapterPattern. */
	@SerialName("pattern") Pattern(RtfSplitStrategy.Pattern),
	@SerialName("single") Single(RtfSplitStrategy.SingleScene),
}

@Serializable
class ProjectImportInput(
	/** Left out, the title the file gives itself. */
	val name: String? = null,
	val format: ImportFileFormat,
	@Serializable(with = Base64Bytes::class)
	val content: ByteArray,
	val markdownSplit: MarkdownSplit = MarkdownSplit.Auto,
	val rtfSplit: RtfSplit = RtfSplit.Formatting,
	/** A regular expression for the lines that start a chapter; left out, chapter, part, prologue, and epilogue. */
	val chapterPattern: String? = null,
	/** Put each chapter's scenes in a group. */
	val chapterGroups: Boolean = false,
)

@Serializable
data class ProjectImported(val name: String, val scenes: Int)
