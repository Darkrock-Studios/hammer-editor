package com.darkrockstudios.apps.hammer.desktop

import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.runtime.ExperimentalComposeApi
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import com.arkivanov.decompose.DecomposeSettings
import com.arkivanov.decompose.ExperimentalDecomposeApi
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.getAndUpdate
import com.darkrockstudios.apps.hammer.base.BuildMetadata
import com.darkrockstudios.apps.hammer.common.AppCloseManager
import com.darkrockstudios.apps.hammer.common.compose.getDefaultDispatcher
import com.darkrockstudios.apps.hammer.common.compose.getMainDispatcher
import com.darkrockstudios.apps.hammer.common.compose.theme.AppTheme
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.globalsettings.UiTheme
import com.darkrockstudios.apps.hammer.common.data.migrator.DataMigrator
import com.darkrockstudios.apps.hammer.common.data.projectmetadata.ProjectMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.dependencyinjection.NapierLogger
import com.darkrockstudios.apps.hammer.common.dependencyinjection.appModule
import com.darkrockstudios.apps.hammer.common.dependencyinjection.imageLoadingModule
import com.darkrockstudios.apps.hammer.common.dependencyinjection.mainModule
import com.darkrockstudios.apps.hammer.common.getInDevelopmentMode
import com.darkrockstudios.apps.hammer.common.getLogDirectory
import com.darkrockstudios.apps.hammer.common.logStartupBanner
import com.darkrockstudios.apps.hammer.common.platformStartupInfo
import com.darkrockstudios.apps.hammer.common.setInDevelopmentMode
import com.darkrockstudios.apps.hammer.desktop.aboutlibraries.aboutLibrariesModule
import com.darkrockstudios.apps.hammer.desktop.sandbox.SandboxStartup
import com.darkrockstudios.apps.hammer.desktop.shortcuts.QuickShortcuts
import dev.nucleusframework.application.NucleusApplicationScope
import dev.nucleusframework.application.NucleusBackend
import dev.nucleusframework.application.nucleusApplication
import dev.nucleusframework.darkmodedetector.isSystemInDarkMode
import dev.nucleusframework.window.NucleusDecoratedWindowTheme
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import io.github.vinceglb.filekit.FileKit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.koin.core.context.GlobalContext
import org.koin.java.KoinJavaComponent.getKoin
import java.io.File
import java.util.logging.ConsoleHandler
import java.util.logging.Level
import kotlin.system.exitProcess
import kotlin.time.Clock

private fun handleArguments(args: Array<String>): DesktopLaunchArgs {
	val launchArgs = parseDesktopLaunchArgs(args)
	setInDevelopmentMode(launchArgs.devMode)
	return launchArgs
}

private fun setupLogging(appScope: CoroutineScope) {
	val consoleHandler = ConsoleHandler()
	consoleHandler.level = if(getInDevelopmentMode()) {
		Level.ALL
	} else {
		Level.INFO
	}

	Napier.base(DebugAntilog(handler = listOf(consoleHandler, FileLogger(scope = appScope))))
}

/**
 * Catch, log, and die on any otherwise-unhandled exception. The async [FileLogger] can't be
 * relied on to flush before the process exits (and packaged builds have no visible stderr), so
 * we also write a synchronous crash dump straight to disk before terminating.
 */
private fun installGlobalExceptionHandler() {
	val previous = Thread.getDefaultUncaughtExceptionHandler()
	Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
		runCatching { Napier.e("Uncaught exception on thread '${thread.name}', terminating", throwable) }
		runCatching { writeCrashDump(thread, throwable) }
		runCatching { previous?.uncaughtException(thread, throwable) }
		exitProcess(1)
	}
}

/** Synchronous, self-contained crash record in the logs dir — the guaranteed artifact when the app dies. */
private fun writeCrashDump(thread: Thread, throwable: Throwable) {
	val dir = getLogDirectory() ?: return
	File(dir).mkdirs()
	File(dir, "crash-${System.currentTimeMillis()}.txt").writeText(
		buildString {
			append("Hammer v${BuildMetadata.APP_VERSION} | ${platformStartupInfo()}\n")
			append("Uncaught exception on thread '${thread.name}'\n\n")
			append(throwable.stackTraceToString())
		}
	)
}

/**
 * For sandboxed Mac App Store builds, JNA's default behavior of extracting
 * libjnidispatch.jnilib to a temp dir at runtime is blocked. We pre-bundle
 * the arm64 jnilib in desktop/resources/macos/, which the Compose Desktop
 * plugin installs into Contents/app/resources/ and signs as part of the
 * .app bundle. Pointing JNA at that location before any JNA class loads
 * makes it use the pre-signed copy instead of trying to extract.
 */
private fun configureJnaForPackagedRuntime() {
	val resourcesDir = System.getProperty("compose.application.resources.dir") ?: return
	System.setProperty("jna.boot.library.path", resourcesDir)
	System.setProperty("jna.library.path", resourcesDir)
}

@ExperimentalDecomposeApi
@ExperimentalMaterialApi
@ExperimentalComposeApi
fun main(args: Array<String>) {
	configureJnaForPackagedRuntime()
	FileKit.init(appId = "com.darkrockstudios.apps.hammer")
	val launchArgs = handleArguments(args)

	val appScope = CoroutineScope(Dispatchers.Default)
	setupLogging(appScope)
	logStartupBanner()
	installGlobalExceptionHandler()

	GlobalContext.startKoin {
		logger(NapierLogger())
		modules(mainModule, imageLoadingModule, aboutLibrariesModule, desktopModule, appModule(appScope))
	}

	SandboxStartup.ensureProjectsDirAccess()

	Napier.i("Startup: running data migration")
	runBlocking { getKoin().get<DataMigrator>(DataMigrator::class).handleDataMigration() }

	val initialProject: ProjectDef? = launchArgs.projectName?.let { name ->
		val match = getKoin().get<ProjectsRepository>().findProject(name)
		if (match == null) Napier.w("Launch arg --project requested missing project: $name")
		match
	}

	if (initialProject != null) {
		runCatching {
			getKoin().get<ProjectMetadataDatasource>().updateMetadata(initialProject) { metadata ->
				metadata.copy(info = metadata.info.copy(lastAccessed = Clock.System.now()))
			}
		}.onFailure { Napier.w("Failed to bump lastAccessed for --project launch", it) }
	}

	Napier.i("Startup: initializing quick shortcuts")
	val quickShortcuts = getKoin().get<QuickShortcuts>()
	quickShortcuts.init()

	if (initialProject == null) {
		// When opening a project, the subsequent ApplicationState.openProject() will refresh.
		appScope.launch { quickShortcuts.refresh() }
	}

	val scope = CoroutineScope(getDefaultDispatcher())
	val mainDispatcher = getMainDispatcher()

	// Listen and react to Global Settings updates
	val globalSettingsStore = getKoin().get<GlobalSettingsStore>()
	val globalSettings = MutableValue(globalSettingsStore.globalSettings)
	val settingsUpdateJob = scope.launch {
		globalSettingsStore.globalSettingsUpdates.collect { settings ->
			withContext(mainDispatcher) {
				globalSettings.getAndUpdate { settings }
			}
		}
	}

	// Decompose's ServiceLoader-provided checker only knows the AWT EDT, but the
	// Tao backend drives the UI (and Dispatchers.Main) from Tao's own main thread.
	DecomposeSettings.update { it.copy(mainThreadCheckEnabled = false) }

	Napier.i("Startup: entering Compose application")
	nucleusApplication(
		args = args,
		backend = NucleusBackend.Tao,
		enableSingleInstance = false,
	) {
		LaunchedEffect(Unit) { Napier.i("Startup: first composition") }
		val applicationState = remember {
			ApplicationState(
				appScope = appScope,
				quickShortcuts = quickShortcuts,
				initialProject = initialProject,
				pendingDeepLink = if (initialProject != null) launchArgs.deepLink else null,
			)
		}
		val imageLoader: ImageLoader = getKoin().get()

		setSingletonImageLoaderFactory { imageLoader }

		LaunchedEffect(quickShortcuts) {
			quickShortcuts.projectClicks.collect { def -> applicationState.openProject(def) }
		}

		val settingsState by globalSettings.subscribeAsState()
		val systemDark = isSystemInDarkMode()
		val darkMode = when (settingsState.uiTheme) {
			UiTheme.Light -> false
			UiTheme.Dark -> true
			UiTheme.FollowSystem -> systemDark
		}
		NucleusDecoratedWindowTheme(isDark = darkMode) {
			AppTheme(useDarkTheme = darkMode, settings = settingsState) {
				when (val windowState = applicationState.windows.value) {
					is WindowState.ProjectSectionWindow -> {
						var showSplash by remember { mutableStateOf(true) }
						if (showSplash) {
							SplashWindow(onFinished = { showSplash = false })
						} else {
							ProjectSelectionWindow(
								settings = settingsState,
								darkMode = darkMode,
							) { project ->
								applicationState.openProject(project)
							}
						}
					}

					is WindowState.ProjectWindow -> {
						ProjectEditorWindow(
							app = applicationState,
							projectDef = windowState.projectDef,
							settings = settingsState,
							darkMode = darkMode,
						)
					}
				}
			}
		}
	}

	settingsUpdateJob.cancel()
	scope.cancel("Program ending")
	quickShortcuts.dispose()
	appScope.cancel("Program ending")
}

internal enum class ConfirmCloseResult {
	SaveAll,
	Discard,
	Cancel
}

internal fun NucleusApplicationScope.performClose(
	app: ApplicationState,
	closeType: ApplicationState.CloseType
) {
	when (closeType) {
		ApplicationState.CloseType.Application -> {
			app.closeProject()
			exitApplication()
		}
		ApplicationState.CloseType.Project -> app.closeProject()
		ApplicationState.CloseType.None -> {
			/* noop */
		}
	}
}

internal fun NucleusApplicationScope.onRequestClose(
	component: AppCloseManager,
	app: ApplicationState,
	closeType: ApplicationState.CloseType
) {
	app.showConfirmProjectClose(closeType)
}
