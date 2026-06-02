package com.darkrockstudios.apps.hammer.android

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.arkivanov.decompose.retainedComponent
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.getAndUpdate
import com.arkivanov.essenty.statekeeper.getSerializable
import com.arkivanov.essenty.statekeeper.putSerializable
import com.darkrockstudios.apps.hammer.android.shortcuts.ProjectShortcutsManager
import com.darkrockstudios.apps.hammer.common.components.projectroot.ProjectDeepLink
import com.darkrockstudios.apps.hammer.common.components.projectroot.ProjectRoot
import com.darkrockstudios.apps.hammer.common.components.projectroot.ProjectRootComponent
import com.darkrockstudios.apps.hammer.common.compose.theme.AppTheme
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.closeProjectScope
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsRepository
import com.darkrockstudios.apps.hammer.common.data.globalsettings.UiTheme
import com.darkrockstudios.apps.hammer.common.data.openProjectScope
import com.darkrockstudios.apps.hammer.common.data.projectmetadata.ProjectMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import com.darkrockstudios.apps.hammer.common.injectMainDispatcher
import com.darkrockstudios.apps.hammer.common.projectroot.ProjectRootScaffold
import com.darkrockstudios.apps.hammer.common.util.AndroidSettingsKeys
import com.russhwolf.settings.Settings
import io.github.aakira.napier.Napier
import kotlinx.coroutines.*
import org.koin.android.ext.android.inject
import org.koin.core.component.getScopeId
import org.koin.java.KoinJavaComponent.getKoin
import kotlin.time.Clock

class ProjectRootActivity : AppCompatActivity() {

	private val settings: Settings by inject()
	private val globalSettingsRepository: GlobalSettingsRepository by inject()
	private val shortcutsManager: ProjectShortcutsManager by inject()
	private val projectsRepository: ProjectsRepository by inject()
	private val projectMetadataDatasource: ProjectMetadataDatasource by inject()
	private val mainDispatcher by injectMainDispatcher()
	private val globalSettings = MutableValue(globalSettingsRepository.globalSettings)
	private var settingsUpdateJob: Job? = null

	private val viewModel: ProjectRootViewModel by viewModels()

	private var projectRoot: ProjectRoot? = null

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()

		val projectDef = resolveProjectDef(intent)
		if (projectDef == null) {
			finish()
		} else {
			viewModel.setProjectDef(projectDef)

			val fromShortcut = intent.action == ACTION_OPEN_PROJECT
			lifecycleScope.launch {
				if (fromShortcut) bumpLastAccessed(projectDef)
				shortcutsManager.refresh()
			}

			val deepLink = resolveDeepLink(intent)
			val component = retainedComponent { componentContext ->
				ProjectRootComponent(
					componentContext = componentContext,
					projectDef = projectDef,
					addMenu = { /* Not needed on Android */ },
					removeMenu = { /* Not needed on Android */ },
					onCloseProject = { projectRoot?.requestClose() },
					initialDeepLink = deepLink,
				)
			}
			projectRoot = component

			setContent {
				val settingsState by globalSettings.subscribeAsState()
				val isDark = when (settingsState.uiTheme) {
					UiTheme.Light -> false
					UiTheme.Dark -> true
					UiTheme.FollowSystem -> isSystemInDarkTheme()
				}

				// Dynamic color is available on Android 12+
				val localCtx = LocalContext.current
				fun getDynamicColorScheme(useDark: Boolean): ColorScheme? {
					val dynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
					return when {
						dynamicColor && useDark -> dynamicDarkColorScheme(localCtx)
						dynamicColor && !useDark -> dynamicLightColorScheme(localCtx)
						else -> null
					}
				}

				AppTheme(settingsState, isDark, ::getDynamicColorScheme) {
					Content(component)
				}
			}
		}
	}

	private fun resolveProjectDef(intent: Intent): ProjectDef? {
		intent.extras?.getSerializable(EXTRA_PROJECT, ProjectDef.serializer())?.let { return it }

		val name = intent.getStringExtra(EXTRA_PROJECT_NAME)?.takeIf { it.isNotBlank() } ?: return null
		val match = projectsRepository.getProjects().firstOrNull { it.name == name }
		if (match == null) Napier.w("Project shortcut for missing project: $name")
		return match
	}

	private fun resolveDeepLink(intent: Intent): ProjectDeepLink? {
		val sceneId = intent.getIntExtra(EXTRA_DEEP_LINK_SCENE_ID, -1)
		return if (sceneId > 0) ProjectDeepLink.Scene(sceneId) else null
	}

	private suspend fun bumpLastAccessed(projectDef: ProjectDef) = withContext(Dispatchers.IO) {
		runCatching {
			projectMetadataDatasource.updateMetadata(projectDef) { metadata ->
				metadata.copy(info = metadata.info.copy(lastAccessed = Clock.System.now()))
			}
		}.onFailure { Napier.w("Failed to bump lastAccessed for shortcut launch: ${projectDef.name}", it) }
	}

	override fun onStart() {
		super.onStart()

		settingsUpdateJob = lifecycleScope.launch {
			globalSettingsRepository.globalSettingsUpdates.collect { settings ->
				withContext(mainDispatcher) {
					globalSettings.getAndUpdate { settings }
				}
			}
		}
	}

	public override fun onResume() {
		super.onResume()

		val keepScreenOn = settings.getBoolean(AndroidSettingsKeys.KEY_SCREEN_ON, false)
		if (keepScreenOn) {
			window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
		}
	}

	public override fun onPause() {
		super.onPause()
		window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
	}

	override fun onStop() {
		super.onStop()
		settingsUpdateJob?.cancel()
		settingsUpdateJob = null
	}

	override fun dispatchKeyEvent(event: KeyEvent): Boolean {
		if (event.action == KeyEvent.ACTION_DOWN &&
			event.keyCode == KeyEvent.KEYCODE_F &&
			event.isShiftPressed &&
			event.isCtrlPressed
		) {
			projectRoot?.let {
				it.showGlobalSearch()
				return true
			}
		}
		return super.dispatchKeyEvent(event)
	}

	@Composable
	private fun Content(
		component: ProjectRoot,
	) {
		val shouldConfirmClose by component.closeRequestHandlers.subscribeAsState()
		val backEnabled by component.backEnabled.subscribeAsState()

		val imageLoader: ImageLoader = getKoin().get()
		setSingletonImageLoaderFactory { imageLoader }

		// Only intercept back when at Home AND there's potential unsaved work to confirm
		// Otherwise, let Android's default back behavior close the activity
		BackHandler(enabled = backEnabled && (component.hasUnsavedBuffers() || shouldConfirmClose.isNotEmpty())) {
			component.requestClose()
		}

		ProjectRootScaffold(component, onCloseRequest = ::finish)
	}

	companion object {
		const val EXTRA_PROJECT = "project"
		const val EXTRA_PROJECT_NAME = "project_name"
		const val EXTRA_DEEP_LINK_SCENE_ID = "deep_link_scene_id"
		const val ACTION_OPEN_PROJECT = "com.darkrockstudios.apps.hammer.android.OPEN_PROJECT"

		fun createIntent(
			context: Context,
			projectDef: ProjectDef,
			deepLinkSceneId: Int? = null,
		): Intent {
			val intent = Intent(context, ProjectRootActivity::class.java)
			val extras = Bundle().apply {
				putSerializable(EXTRA_PROJECT, projectDef, ProjectDef.serializer())
				if (deepLinkSceneId != null) putInt(EXTRA_DEEP_LINK_SCENE_ID, deepLinkSceneId)
			}
			intent.putExtras(extras)
			return intent
		}

		fun createShortcutIntent(context: Context, projectName: String): Intent =
			Intent(context, ProjectRootActivity::class.java)
				.setAction(ACTION_OPEN_PROJECT)
				.putExtra(EXTRA_PROJECT_NAME, projectName)
	}
}

class ProjectRootViewModel : ViewModel() {

	private var projectDef: ProjectDef? = null
	fun setProjectDef(project: ProjectDef) {
		if (projectDef == null) {
			projectDef = project
			runBlocking { openProjectScope(project) }
		}
	}

	override fun onCleared() {
		projectDef?.let {
			closeProjectScope(getKoin().getScope(ProjectDefScope(it).getScopeId()), it)
		}
	}
}

