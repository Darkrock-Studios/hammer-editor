package com.darkrockstudios.apps.hammer.operations.core

import com.darkrockstudios.apps.hammer.base.http.storyideas.StoryIdea
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.SyncedProjectDefinition
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.projectmetadata.ProjectMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.ProjectSyncOutcome
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.SyncAccountListener
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.SyncAccountUseCase
import com.darkrockstudios.apps.hammer.common.data.sync.ideassync.IdeaConflict
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.ProjectSynchronizationData
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncDataDatasource
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncLogMessage
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import com.darkrockstudios.apps.hammer.operations.Access
import com.darkrockstudios.apps.hammer.operations.NoInput
import com.darkrockstudios.apps.hammer.operations.Operation
import com.darkrockstudios.apps.hammer.operations.OperationContext
import com.darkrockstudios.apps.hammer.operations.OperationScope
import com.darkrockstudios.apps.hammer.operations.OperationException
import com.darkrockstudios.apps.hammer.operations.operation
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import okio.FileSystem
import okio.IOException
import kotlin.time.Instant

// Neither is agent-visible: syncing sends the writer's work to a server.
internal fun syncOperations(): List<Operation<*, *>> = listOf(
	operation<NoInput, SyncStatus>(
		name = "sync.status",
		description = "Each project's link to the sync server, its last sync, and how many of its changes are waiting to sync.",
		access = Access.Read,
		scope = OperationScope.Account,
	) {
		val metadata = koinGet<ProjectMetadataDatasource>()
		val projects = koinGet<ProjectsRepository>().getProjects().map { def ->
			val journal = readJournal(def)
			ProjectSyncStatus(
				name = def.name,
				linked = metadata.readMetadata(def)?.info?.serverProjectId != null,
				lastSync = journal?.lastSync?.takeIf { it != Instant.DISTANT_PAST },
				pendingChanges = journal?.let { (it.dirty.map { entity -> entity.id }.toSet() + it.newIds).size } ?: 0,
			)
		}
		SyncStatus(projects.sortedBy { it.name.lowercase() })
	},
	SyncRunOperation(),
)

private fun readJournal(def: ProjectDef): ProjectSynchronizationData? {
	val path = def.path.toOkioPath() / SyncDataDatasource.SYNC_FILE_NAME
	val fileSystem = koinGet<FileSystem>()
	if (!fileSystem.exists(path)) return null
	return try {
		fileSystem.read(path) { koinGet<Json>().decodeFromString(ProjectSynchronizationData.serializer(), readUtf8()) }
	} catch (e: IOException) {
		null
	} catch (e: SerializationException) {
		null
	}
}

/** Exit codes: 1 when anything failed, 2 when a project needs resolving in the app, and 0 otherwise. */
private class SyncRunOperation : Operation<SyncRunInput, SyncRunResult> {
	override val name = "sync.run"
	override val description =
		"Sync the account and every linked project with the server, as Sync all does. A project with a conflict is left for the app."
	override val input: KSerializer<SyncRunInput> = serializer()
	override val output: KSerializer<SyncRunResult> = serializer()
	override val access = Access.Write
	override val scope = OperationScope.Account

	override fun exitCode(output: SyncRunResult): Int = when {
		!output.accountSynced || !output.ideasSynced || output.projects.any { it.outcome == SyncOutcome.Failed } -> 1
		output.projects.any { it.outcome == SyncOutcome.NeedsResolution } -> 2
		else -> 0
	}

	override suspend fun run(context: OperationContext, input: SyncRunInput): SyncRunResult {
		val settings = koinGet<GlobalSettingsStore>().serverSettings
		if (settings == null || settings.userId < 0 || settings.bearerToken == null) {
			throw OperationException(OperationException.Kind.Unauthorized, "Not logged in. Run 'hammer account login' first.")
		}
		val only = input.project?.let(context.projects::resolve)
		val filter: (SyncedProjectDefinition) -> Boolean = { only == null || it.projectDef == only }

		val log = mutableListOf<String>()
		var unauthorized = false
		val listener = object : SyncAccountListener {
			override suspend fun onLog(message: SyncLogMessage) {
				log += listOfNotNull(message.level.name, message.projectName, message.message).joinToString(": ")
			}

			override suspend fun onProjectsDiscovered(projects: List<ProjectDef>) = Unit
			override suspend fun onProjectProgress(projectDef: ProjectDef, progress: Float?) = Unit
			override suspend fun onProjectOutcome(projectDef: ProjectDef, outcome: ProjectSyncOutcome) = Unit

			override suspend fun onUnauthorized() {
				unauthorized = true
			}

			override suspend fun onIdeaConflict(conflict: IdeaConflict): StoryIdea? = when (input.onConflict) {
				ConflictChoice.Abort -> null
				ConflictChoice.Local -> conflict.local
				ConflictChoice.Server -> conflict.server
			}
		}

		val result = koinGet<SyncAccountUseCase>().execute(listener, filter)
		if (unauthorized) {
			throw OperationException(OperationException.Kind.Unauthorized, "The server no longer accepts this login. Run 'hammer account login'.")
		}
		return SyncRunResult(
			accountSynced = result.accountSuccess,
			ideasSynced = result.ideasSuccess,
			projects = result.projects.map { (name, outcome) -> ProjectSyncResult(name, SyncOutcome.of(outcome)) }.sortedBy { it.name },
			log = log,
		)
	}
}

@Serializable
data class SyncStatus(val projects: List<ProjectSyncStatus>)

@Serializable
data class ProjectSyncStatus(
	val name: String,
	val linked: Boolean,
	/** Null when never synced. */
	val lastSync: Instant?,
	val pendingChanges: Int,
)

@Serializable
enum class ConflictChoice {
	/** Leave it for the app: a project stops, an idea stays unsynced. */
	@SerialName("abort") Abort,

	/** Keep this device's version. Applies to ideas; a project conflict always stops the project. */
	@SerialName("local") Local,

	/** Keep the server's version. Applies to ideas; a project conflict always stops the project. */
	@SerialName("server") Server,
}

@Serializable
data class SyncRunInput(
	/** Sync only this project; account changes and ideas still sync. */
	val project: String? = null,
	val onConflict: ConflictChoice = ConflictChoice.Abort,
)

@Serializable
enum class SyncOutcome {
	@SerialName("success") Success,
	@SerialName("unchanged") Unchanged,
	@SerialName("needs_resolution") NeedsResolution,
	@SerialName("failed") Failed,
	@SerialName("not_on_server") NotOnServer,
	@SerialName("skipped") Skipped;

	companion object {
		fun of(outcome: ProjectSyncOutcome): SyncOutcome = when (outcome) {
			ProjectSyncOutcome.Success -> Success
			ProjectSyncOutcome.Unchanged -> Unchanged
			ProjectSyncOutcome.NeedsResolution -> NeedsResolution
			ProjectSyncOutcome.Failed -> Failed
			ProjectSyncOutcome.NotOnServer -> NotOnServer
			ProjectSyncOutcome.Skipped -> Skipped
		}
	}
}

@Serializable
data class ProjectSyncResult(val name: String, val outcome: SyncOutcome)

@Serializable
data class SyncRunResult(
	val accountSynced: Boolean,
	val ideasSynced: Boolean,
	val projects: List<ProjectSyncResult>,
	val log: List<String>,
)
