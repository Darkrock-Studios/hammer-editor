package com.darkrockstudios.apps.hammer.operations.core

import com.darkrockstudios.apps.hammer.base.http.projectdata.WordCountGoal
import com.darkrockstudios.apps.hammer.common.data.ExportOptions
import com.darkrockstudios.apps.hammer.common.data.export.ExportStoryUseCase
import com.darkrockstudios.apps.hammer.common.data.export.StoryExporterRegistry
import com.darkrockstudios.apps.hammer.common.data.export.exportFileName
import com.darkrockstudios.apps.hammer.common.data.projectbackup.ProjectBackupRepository
import com.darkrockstudios.apps.hammer.common.data.projectdata.readStoredProjectData
import com.darkrockstudios.apps.hammer.common.data.projectmetadata.ProjectMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.data.projectstatistics.ProjectStatisticsCacheReader
import com.darkrockstudios.apps.hammer.operations.Access
import com.darkrockstudios.apps.hammer.operations.Base64Bytes
import com.darkrockstudios.apps.hammer.operations.NoInput
import com.darkrockstudios.apps.hammer.operations.Operation
import com.darkrockstudios.apps.hammer.operations.OperationContext
import com.darkrockstudios.apps.hammer.operations.OperationScope
import com.darkrockstudios.apps.hammer.operations.invalidInput
import com.darkrockstudios.apps.hammer.operations.jsonSchema
import com.darkrockstudios.apps.hammer.operations.operation
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.serializer
import net.peanuuutz.tomlkt.Toml
import okio.FileSystem
import kotlin.time.Instant

internal fun projectOperations(): List<Operation<*, *>> = listOf(
	operation<NoInput, ProjectList>(
		name = "project.list",
		description = "List every project with its word count and when it was last edited.",
		access = Access.Read,
		scope = OperationScope.Content,
	) {
		val metadata = koinGet<ProjectMetadataDatasource>()
		val stats = koinGet<ProjectStatisticsCacheReader>()
		val projects = koinGet<ProjectsRepository>().getProjects().map { def ->
			val info = metadata.readMetadata(def)?.info
			val cached = stats.loadStatistics(def)
			ProjectSummary(
				name = def.name,
				serverProjectId = info?.serverProjectId?.id,
				wordCount = cached?.totalWords,
				created = info?.created,
				lastAccessed = info?.lastAccessed,
				lastEdited = cached?.lastEditedAt,
			)
		}
		ProjectList(projects.sortedBy { it.name.lowercase() })
	},
	operation<ProjectInput, ProjectInfo>(
		name = "project.info",
		description = "A project's details: author, language, tags, word count goal, cached totals, and sync linkage.",
		access = Access.Read,
		scope = OperationScope.Content,
	) { input ->
		val def = projects.resolve(input.project)
		val info = koinGet<ProjectMetadataDatasource>().readMetadata(def)?.info
		val data = readStoredProjectData(def, koinGet<FileSystem>(), koinGet<Toml>()).data
		val cached = koinGet<ProjectStatisticsCacheReader>().loadStatistics(def)
		ProjectInfo(
			name = def.name,
			serverProjectId = info?.serverProjectId?.id,
			created = info?.created,
			lastAccessed = info?.lastAccessed,
			authorName = data.authorName,
			language = data.language,
			tags = data.tags.sorted(),
			wordCountGoal = data.wordCountGoal?.let(::WordGoal),
			wordCount = cached?.totalWords,
			sceneCount = cached?.numberOfScenes,
			lastEdited = cached?.lastEditedAt,
		)
	},
	ProjectExportOperation(),
	operation<NoInput, ExportFormats>(
		name = "export.formats",
		description = "The formats project.export can produce.",
		access = Access.Read,
		scope = OperationScope.Content,
	) {
		ExportFormats(
			koinGet<StoryExporterRegistry>().exporters.map {
				ExportFormat(id = it.formatId, fileExtension = it.fileExtension, mimeType = it.mimeType)
			}
		)
	},
	operation<ProjectInput, Backups>(
		name = "backup.list",
		description = "A project's backups, oldest first.",
		access = Access.Read,
		scope = OperationScope.Content,
	) { input ->
		val def = projects.resolve(input.project)
		Backups(koinGet<ProjectBackupRepository>().getBackups(def).map { Backup(it.date) })
	},
)

private class ProjectExportOperation : Operation<ProjectExportInput, ExportedFile> {
	override val name = "project.export"
	override val description = "Export a project, or some of its scenes, in one of the formats from export.formats."
	override val input: KSerializer<ProjectExportInput> = serializer()
	override val output: KSerializer<ExportedFile> = serializer()
	override val access = Access.Read
	override val scope = OperationScope.Content

	override fun inputSchema(): JsonObject {
		val schema = jsonSchema(input.descriptor)
		val formats = JsonArray(koinGet<StoryExporterRegistry>().exporters.map { JsonPrimitive(it.formatId) })
		val properties = schema.getValue("properties").jsonObject
		val format = JsonObject(properties.getValue("format").jsonObject + ("enum" to formats))
		return JsonObject(schema + ("properties" to JsonObject(properties + ("format" to format))))
	}

	override suspend fun run(context: OperationContext, input: ProjectExportInput): ExportedFile {
		val registry = koinGet<StoryExporterRegistry>()
		if (!registry.isRegistered(input.format)) {
			invalidInput("Unknown export format '${input.format}'; see export.formats")
		}
		val exporter = registry.forFormat(input.format)
		val options = ExportOptions(
			treatTopLevelAsChapters = input.treatTopLevelAsChapters,
			format = input.format,
			sceneIds = input.sceneIds?.toSet(),
		)
		return context.projects.withProject(input.project) { project ->
			val content = project.scope.get<ExportStoryUseCase>().render(options).readByteArray()
			ExportedFile(
				fileName = exportFileName(project.def.name, exporter.fileExtension),
				mimeType = exporter.mimeType,
				content = content,
			)
		}
	}
}

@Serializable
data class ProjectList(val projects: List<ProjectSummary>)

@Serializable
data class ProjectSummary(
	val name: String,
	/** Null when the project is not linked to a sync server. */
	val serverProjectId: String?,
	/** From the statistics cache; null until statistics are first calculated. */
	val wordCount: Int?,
	/** Null when the project's metadata can't be read. */
	val created: Instant?,
	val lastAccessed: Instant?,
	val lastEdited: Instant?,
)

@Serializable
data class ProjectInfo(
	val name: String,
	val serverProjectId: String?,
	val created: Instant?,
	val lastAccessed: Instant?,
	val authorName: String?,
	val language: String?,
	val tags: List<String>,
	val wordCountGoal: WordGoal?,
	val wordCount: Int?,
	val sceneCount: Int?,
	val lastEdited: Instant?,
)

@Serializable
data class WordGoal(val cadence: Cadence, val count: Int) {
	constructor(goal: WordCountGoal) : this(
		cadence = when (goal.cadence) {
			WordCountGoal.Cadence.DAY -> Cadence.Day
			WordCountGoal.Cadence.WEEK -> Cadence.Week
		},
		count = goal.count,
	)

	@Serializable
	enum class Cadence {
		@SerialName("day") Day,
		@SerialName("week") Week,
	}
}

@Serializable
data class ProjectExportInput(
	val project: String,
	/** An id from export.formats. */
	val format: String,
	/** Limits the export to these scenes; omitted exports the whole story. */
	val sceneIds: List<Int>? = null,
	val treatTopLevelAsChapters: Boolean = true,
)

@Serializable
class ExportedFile(
	val fileName: String,
	val mimeType: String,
	@Serializable(with = Base64Bytes::class)
	val content: ByteArray,
)

@Serializable
data class ExportFormats(val formats: List<ExportFormat>)

@Serializable
data class ExportFormat(val id: String, val fileExtension: String, val mimeType: String)

@Serializable
data class Backups(val backups: List<Backup>)

@Serializable
data class Backup(val date: Instant)
