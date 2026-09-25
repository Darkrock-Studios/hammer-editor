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
import com.darkrockstudios.apps.hammer.common.AppCloseManager
import com.darkrockstudios.apps.hammer.common.compose.getDefaultDispatcher
import com.darkrockstudios.apps.hammer.common.compose.getMainDispatcher
import com.darkrockstudios.apps.hammer.common.compose.plugin.installedPluginUis
import com.darkrockstudios.apps.hammer.common.compose.plugin.installedPlugins
import com.darkrockstudios.apps.hammer.common.compose.plugin.pluginUiModule
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
import com.darkrockstudios.apps.hammer.common.getConfigDirectory
import com.darkrockstudios.apps.hammer.common.getInDevelopmentMode
import com.darkrockstudios.apps.hammer.common.getLogDirectory
import com.darkrockstudios.apps.hammer.common.logStartupBanner
import com.darkrockstudios.apps.hammer.common.setInDevelopmentMode
import com.darkrockstudios.apps.hammer.common.startupBanner
import com.darkrockstudios.apps.hammer.desktop.aboutlibraries.aboutLibrariesModule
import com.darkrockstudios.apps.hammer.desktop.cli.Cli
import com.darkrockstudios.apps.hammer.desktop.cli.WriterLock
import com.darkrockstudios.apps.hammer.desktop.plugin.installedDesktopPlugins
import com.darkrockstudios.apps.hammer.desktop.sandbox.SandboxStartup
import com.darkrockstudios.apps.hammer.desktop.shortcuts.QuickShortcuts
import com.darkrockstudios.apps.hammer.operations.plugin.PluginRegistry
import com.darkrockstudios.apps.hammer.plugins.wasmhost.RuntimePlugins
import dev.nucleusframework.application.NucleusApplicationScope
import dev.nucleusframework.application.NucleusBackend
import dev.nucleusframework.application.nucleusApplication
import dev.nucleusframework.darkmodedetector.isSystemInDarkMode
import dev.nucleusframework.window.NucleusDecoratedWindowTheme
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import io.github.vinceglb.filekit.FileKit
import java.io.File
import java.io.IOException
import java.util.logging.ConsoleHandler
import java.util.logging.Level
import kotlin.system.exitProcess
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okio.FileSystem
import org.koin.core.context.GlobalContext
import org.koin.java.KoinJavaComponent.getKoin

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
			append(startupBanner() + "\n")
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

/** Logs go to the log file only, since stdout carries the command's JSON, and are flushed before exit. */
private fun runCli(args: Array<String>): Int {
	val logScope = CoroutineScope(Dispatchers.IO)
	val logger = FileLogger(scope = logScope)
	Napier.base(DebugAntilog(handler = listOf(logger)))
	val code = Cli.run(args.toList())
	logger.close()
	runBlocking { withTimeoutOrNull(2.seconds) { logScope.coroutineContext.job.children.forEach { it.join() } } }
	return code
}

/** Waits briefly for a CLI call to finish. Runs without the lock when it cannot be had, as before it existed. */
private fun acquireAppWriterLock(): WriterLock? = try {
	when (val result = WriterLock.acquire(File(getConfigDirectory()), WriterLock.Holder.App, wait = 3.seconds)) {
		is WriterLock.Result.Acquired -> result.lock
		is WriterLock.Result.Busy -> {
			Napier.w { "Writer lock held by ${result.holder ?: "another process"}; running without it" }
			null
		}
	}
} catch (e: IOException) {
	Napier.w(e) { "Could not take the writer lock; running without it" }
	null
}

/**
 * Held for the app's whole run, so a CLI call cannot write under it; a field so it is never collected.
 * A second window of the app cannot take it and runs without, as it did before the lock existed.
 */
private var appWriterLock: WriterLock? = null

@ExperimentalDecomposeApi
@ExperimentalMaterialApi
@ExperimentalComposeApi
fun main(args: Array<String>) {
	if (Cli.isInvocation(args)) exitProcess(runCli(args))
	configureJnaForPackagedRuntime()
	FileKit.init(appId = "com.darkrockstudios.apps.hammer")
	val launchArgs = handleArguments(args)

	val appScope = CoroutineScope(Dispatchers.Default)
	setupLogging(appScope)
	appWriterLock = acquireAppWriterLock()
	logStartupBanner()
	installGlobalExceptionHandler()

	val compiledInPlugins = installedPlugins() + installedDesktopPlugins()
	val runtimePlugins = RuntimePlugins.inConfigDirectory(FileSystem.SYSTEM, compiledInPlugins)
	val pluginRegistry = PluginRegistry(compiledInPlugins + runtimePlugins.load())

	GlobalContext.startKoin {
		logger(NapierLogger())
		modules(
			listOf(
				mainModule,
				imageLoadingModule,
				aboutLibrariesModule,
				desktopModule,
				appModule(appScope),
				pluginUiModule(installedPluginUis()),
				runtimePlugins.koinModule(),
			) + pluginRegistry.koinModules()
		)
	}

	SandboxStartup.ensureProjectsDirAccess()

	Napier.i("Startup: running data migration")
	runBlocking { getKoin().get<DataMigrator>(DataMigrator::class).handleDataMigration() }
	pluginRegistry.start()

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
		LaunchedEffect(Unit) {
			Napier.i("Startup: first composition")
			// Refreshed from inside composition, never from a coroutine racing
			// the launch: an implementation that touches AWT (the macOS dock
			// menu does) must not get there before the Tao backend has claimed
			// AppKit, or the app hangs with no window. See SandboxStartup.
			// When opening a project, ApplicationState.openProject() refreshes instead.
			if (initialProject == null) quickShortcuts.refresh()
		}
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
						// The real window always exists, merely minimized behind the splash, and
						// the hand-off is timed here rather than inside either window: a window
						// that fails to render can neither stall it nor swallow the app.
						LaunchedEffect(Unit) {
							withContext(Dispatchers.Default) { delay(SplashDurationMs.toLong()) }
							showSplash = false
						}
						ProjectSelectionWindow(
							settings = settingsState,
							darkMode = darkMode,
							minimized = showSplash,
						) { project ->
							applicationState.openProject(project)
						}
						if (showSplash) {
							SplashWindow()
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
