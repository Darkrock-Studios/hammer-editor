package com.darkrockstudios.apps.hammer.wear

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.http.createJsonSerializer
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettings
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.isSuccess
import com.darkrockstudios.apps.hammer.common.data.projectmetadata.ProjectMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.common.util.DeviceLocaleResolver
import com.darkrockstudios.apps.hammer.wear.data.CaptureWriter
import com.darkrockstudios.apps.hammer.wear.data.UnsyncedContentSource
import com.darkrockstudios.apps.hammer.wear.data.WearPrefsDatasource
import com.darkrockstudios.apps.hammer.wear.sync.CaptureSyncScheduler
import com.darkrockstudios.apps.hammer.wear.sync.SyncCoordinator
import com.darkrockstudios.apps.hammer.wear.sync.SyncRunResult
import com.darkrockstudios.apps.hammer.wear.sync.SyncStatus
import com.darkrockstudios.apps.hammer.wear.sync.SyncTrigger
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import okio.IOException
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem

class FakeWearPrefsDatasource : WearPrefsDatasource {
	private val ids = MutableStateFlow<Set<String>>(emptySet())
	private val lastCapture = MutableStateFlow<String?>(null)

	override val subscribedProjectIds: Flow<Set<String>> = ids
	override val lastCaptureProjectId: Flow<String?> = lastCapture

	override suspend fun setSubscribed(projectId: String, subscribed: Boolean) {
		ids.value = if (subscribed) ids.value + projectId else ids.value - projectId
	}

	override suspend fun setLastCaptureProjectId(projectId: String) {
		lastCapture.value = projectId
	}

	override suspend fun clear() {
		ids.value = emptySet()
		lastCapture.value = null
	}
}

class FakeCaptureWriter : CaptureWriter {
	val notes = mutableListOf<Pair<String, String>>()
	val ideas = mutableListOf<String>()
	var succeed = true
	var failWith: Exception? = null

	override suspend fun writeNote(projectDef: ProjectDef, text: String): Boolean {
		failWith?.let { throw it }
		if (succeed) notes += projectDef.name to text
		return succeed
	}

	override suspend fun writeIdea(text: String): Boolean {
		failWith?.let { throw it }
		if (succeed) ideas += text
		return succeed
	}
}

class FakeCaptureSyncScheduler : CaptureSyncScheduler {
	var requests = 0

	override fun syncSoon() {
		requests++
	}
}

/** Blocks [pendingIn] until released, to model counting being slower than the save. */
class GatedUnsyncedContentSource(private val gate: CompletableDeferred<Unit>) : UnsyncedContentSource {
	override suspend fun pendingIn(projectDef: ProjectDef): Int {
		gate.await()
		return 0
	}

	override suspend fun pendingIdeas(): Int {
		gate.await()
		return 0
	}
}

/** Pending counts keyed by project name, so a test can say what has not reached the server. */
class FakeUnsyncedContentSource : UnsyncedContentSource {
	val pendingByProject = mutableMapOf<String, Int>()
	var pendingIdeas = 0
	var failWith: Exception? = null

	/** Counts full sweeps, so a test can show a second one was never started. */
	var pendingCalls = 0

	override suspend fun pendingIn(projectDef: ProjectDef): Int {
		failWith?.let { throw it }
		return pendingByProject[projectDef.name] ?: 0
	}

	override suspend fun pendingIdeas(): Int {
		pendingCalls++
		failWith?.let { throw it }
		return pendingIdeas
	}
}

/** Subscribing works; dropping a subscription does not, which is the unsubscribe failure path. */
class FailingUnsubscribeDatasource : WearPrefsDatasource {
	private val delegate = FakeWearPrefsDatasource()

	override val subscribedProjectIds: Flow<Set<String>> get() = delegate.subscribedProjectIds
	override val lastCaptureProjectId: Flow<String?> get() = delegate.lastCaptureProjectId

	override suspend fun setSubscribed(projectId: String, subscribed: Boolean) {
		if (!subscribed) throw IOException("the preference store is unwritable")
		delegate.setSubscribed(projectId, subscribed)
	}

	override suspend fun setLastCaptureProjectId(projectId: String) =
		delegate.setLastCaptureProjectId(projectId)

	override suspend fun clear() = delegate.clear()
}

class FakeSyncCoordinator : SyncCoordinator {
	override val status = MutableStateFlow(SyncStatus())
	val requested = mutableListOf<SyncTrigger>()
	var autoSyncRequests = 0
	var autoSyncResets = 0

	override fun requestSync(trigger: SyncTrigger) {
		requested += trigger
	}

	override fun requestAutoSync() {
		autoSyncRequests++
	}

	/** Set to mimic a sync that uploads, so a test can clear what was pending. */
	var onSync: (suspend () -> Unit)? = null

	override suspend fun sync(trigger: SyncTrigger): SyncRunResult {
		requested += trigger
		onSync?.invoke()
		return SyncRunResult.Skipped
	}

	override suspend fun <T> runExclusive(block: suspend () -> T): T = block()

	override fun resetAutoSync() {
		autoSyncResets++
	}
}

/** A real [ProjectsRepository] over a fake filesystem. Needs Koin started for its dispatcher. */
class TestProjects {
	val fileSystem = FakeFileSystem().apply { createDirectories("/projects".toPath()) }
	private val toml = createTomlSerializer()
	val metadataDatasource = ProjectMetadataDatasource(fileSystem, toml)
	val repository: ProjectsRepository

	init {
		val globalSettingsStore = mockk<GlobalSettingsStore>()
		every { globalSettingsStore.globalSettings } returns GlobalSettings(projectsDirectory = "/projects")
		every { globalSettingsStore.globalSettingsUpdates } returns MutableSharedFlow()
		repository = ProjectsRepository(
			fileSystem = fileSystem,
			globalSettingsStore = globalSettingsStore,
			projectsMetadataDatasource = metadataDatasource,
			toml = toml,
			json = createJsonSerializer(),
			deviceLocaleResolver = mockk<DeviceLocaleResolver>(relaxed = true),
		)
	}

	fun create(name: String, serverId: String? = null): ProjectDef {
		val result = repository.createProject(name, seedDefaultLanguage = false)
		if (!isSuccess(result)) error("Failed to create $name")
		val projectDef = result.data
		if (serverId != null) {
			metadataDatasource.updateMetadata(projectDef) {
				it.copy(info = it.info.copy(serverProjectId = ProjectId(serverId)))
			}
		}
		return projectDef
	}
}
