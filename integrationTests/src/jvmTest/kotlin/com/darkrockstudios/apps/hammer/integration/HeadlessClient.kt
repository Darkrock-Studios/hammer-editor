package com.darkrockstudios.apps.hammer.integration

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.globalsettings.ServerSettings
import com.darkrockstudios.apps.hammer.common.data.isSuccess
import com.darkrockstudios.apps.hammer.common.data.openProjectScope
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorService
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneRepository
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.ClientProjectSynchronizer
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import okio.Path
import org.koin.core.component.KoinComponent
import org.koin.core.scope.Scope
import org.koin.mp.KoinPlatform.getKoin

/**
 * Drives a fully wired client editor against the in-process Jetty server.
 *
 * Each instance:
 *   - creates a local project on the real filesystem (Okio `FileSystem.SYSTEM`),
 *   - writes [ServerSettings] pointing at `127.0.0.1:54321` with the test's bearer token,
 *   - opens the project's Koin scope so the full sync pipeline is resolvable.
 *
 * The on-disk state under [projectPath] is what tests assert against.
 */
class HeadlessClient private constructor(
	val projectDef: ProjectDef,
	val scope: Scope,
) : KoinComponent {

	val projectPath: Path = projectDef.path.toOkioPath()

	val synchronizer: ClientProjectSynchronizer get() = scope.get()
	val sceneEditor: SceneRepository get() = scope.get()
	val sceneEditorService: SceneEditorService get() = scope.get()

	/**
	 * Runs a full sync against the server. Conflicts are routed through [resolveConflict],
	 * which selects which version "wins". Defaults to picking the server copy.
	 */
	suspend fun sync(
		resolveConflict: (ApiProjectEntity) -> ApiProjectEntity = { it },
	): Boolean {
		val s = synchronizer
		return s.sync(
			onProgress = { _, _ -> },
			onLog = { },
			onConflict = { conflict ->
				// `ClientProjectSynchronizer` expects the resolved entity back via the
				// internal channel rather than as a return value.
				s.resolveConflict(resolveConflict(conflict))
			},
			onComplete = { },
			onlyNew = false,
			onUnauthorized = { },
		)
	}

	fun close() {
		scope.close()
	}

	companion object {
		suspend fun create(projectName: String, serverSettings: ServerSettings): HeadlessClient {
			val koin = getKoin()
			val projectsRepository: ProjectsRepository = koin.get<ProjectsRepository>()
			val globalSettings: GlobalSettingsStore = koin.get<GlobalSettingsStore>()

			val createResult = projectsRepository.createProject(projectName)
			check(isSuccess(createResult)) { "Failed to create local project: $createResult" }
			val projectDef: ProjectDef = createResult.data

			// Persist server settings BEFORE opening the scope so the sync pipeline
			// resolves an HttpClient that points at the test server.
			globalSettings.updateServerSettings(serverSettings)

			// `openProjectScope` is a suspend extension on KoinComponent; use a
			// throwaway anchor so the companion can invoke it.
			val anchor = object : KoinComponent {}
			val scope = with(anchor) { openProjectScope(projectDef) }
			return HeadlessClient(projectDef, scope)
		}
	}
}
