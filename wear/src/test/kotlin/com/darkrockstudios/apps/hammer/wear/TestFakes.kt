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
import com.darkrockstudios.apps.hammer.wear.data.WearPrefsDatasource
import com.darkrockstudios.apps.hammer.wear.sync.SyncCoordinator
import com.darkrockstudios.apps.hammer.wear.sync.SyncRunResult
import com.darkrockstudios.apps.hammer.wear.sync.SyncStatus
import com.darkrockstudios.apps.hammer.wear.sync.SyncTrigger
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem

class FakeWearPrefsDatasource : WearPrefsDatasource {
	private val ids = MutableStateFlow<Set<String>>(emptySet())

	override val subscribedProjectIds: Flow<Set<String>> = ids

	override suspend fun setSubscribed(projectId: String, subscribed: Boolean) {
		ids.value = if (subscribed) ids.value + projectId else ids.value - projectId
	}

	override suspend fun clear() {
		ids.value = emptySet()
	}
}

class FakeSyncCoordinator : SyncCoordinator {
	override val status = MutableStateFlow(SyncStatus())
	val requested = mutableListOf<SyncTrigger>()
	var autoSyncRequests = 0

	override fun requestSync(trigger: SyncTrigger) {
		requested += trigger
	}

	override fun requestAutoSync() {
		autoSyncRequests++
	}

	override suspend fun sync(trigger: SyncTrigger): SyncRunResult {
		requested += trigger
		return SyncRunResult.Skipped
	}

	override suspend fun <T> runExclusive(block: suspend () -> T): T = block()
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
