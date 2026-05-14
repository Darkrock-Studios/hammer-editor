package com.darkrockstudios.apps.hammer.desktop

import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.runtime.ExperimentalComposeApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.application
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import com.arkivanov.decompose.ExperimentalDecomposeApi
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.getAndUpdate
import com.darkrockstudios.apps.hammer.common.AppCloseManager
import com.darkrockstudios.apps.hammer.common.compose.getDefaultDispatcher
import com.darkrockstudios.apps.hammer.common.compose.getMainDispatcher
import com.darkrockstudios.apps.hammer.common.compose.theme.AppTheme
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsRepository
import com.darkrockstudios.apps.hammer.common.data.globalsettings.UiTheme
import com.darkrockstudios.apps.hammer.common.data.migrator.DataMigrator
import com.darkrockstudios.apps.hammer.common.dependencyinjection.NapierLogger
import com.darkrockstudios.apps.hammer.common.dependencyinjection.appModule
import com.darkrockstudios.apps.hammer.common.dependencyinjection.imageLoadingModule
import com.darkrockstudios.apps.hammer.common.dependencyinjection.mainModule
import com.darkrockstudios.apps.hammer.common.getInDevelopmentMode
import com.darkrockstudios.apps.hammer.common.setInDevelopmentMode
import com.darkrockstudios.apps.hammer.desktop.aboutlibraries.aboutLibrariesModule
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import io.github.kdroidfilter.nucleus.darkmodedetector.isSystemInDarkMode
import io.github.kdroidfilter.nucleus.hidpi.getLinuxNativeScaleFactor
import io.github.kdroidfilter.nucleus.window.NucleusDecoratedWindowTheme
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.coroutines.*
import org.koin.core.context.GlobalContext
import org.koin.java.KoinJavaComponent.getKoin
import java.util.logging.ConsoleHandler
import java.util.logging.Level

private fun handleArguments(args: Array<String>) {
	val parser = ArgParser("hammer")

	val devMode by parser.option(
		ArgType.Boolean,
		shortName = "d",
		fullName = "dev",
		description = "Development Mode"
	).default(false)

	parser.parse(args)

	setInDevelopmentMode(devMode)
}

/**
 * Detect the GNOME/KDE compositor scale and feed it to AWT before Swing initializes.
 *
 * OpenJDK on XWayland honors integer `sun.java2d.uiScale` reliably but fractional
 * values (GNOME's 125%/133%/150%) are inconsistent — we round up so 1.333 → 2.
 * JetBrains Runtime detects scale natively and ignores both properties.
 */
private fun configureLinuxHiDpi() {
	if (System.getProperty("sun.java2d.uiScale") != null) return

	val detected = getLinuxNativeScaleFactor()
	if (detected <= 1.0) return

	val rounded = kotlin.math.ceil(detected).toInt()
	System.setProperty("sun.java2d.uiScale.enabled", "true")
	System.setProperty("sun.java2d.uiScale", rounded.toString())
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

@ExperimentalDecomposeApi
@ExperimentalMaterialApi
@ExperimentalComposeApi
fun main(args: Array<String>) {
	configureLinuxHiDpi()

	handleArguments(args)

	val appScope = CoroutineScope(Dispatchers.Default)
	setupLogging(appScope)

	GlobalContext.startKoin {
		logger(NapierLogger())
		modules(mainModule, imageLoadingModule, aboutLibrariesModule, desktopModule, appModule(appScope))
	}

	runBlocking { getKoin().get<DataMigrator>(DataMigrator::class).handleDataMigration() }

	val scope = CoroutineScope(getDefaultDispatcher())
	val mainDispatcher = getMainDispatcher()

	// Listen and react to Global Settings updates
	val globalSettingsRepository = getKoin().get<GlobalSettingsRepository>()
	val globalSettings = MutableValue(globalSettingsRepository.globalSettings)
	val settingsUpdateJob = scope.launch {
		globalSettingsRepository.globalSettingsUpdates.collect { settings ->
			withContext(mainDispatcher) {
				globalSettings.getAndUpdate { settings }
			}
		}
	}

	application {
		val applicationState = remember { ApplicationState() }
		val imageLoader: ImageLoader = getKoin().get()

		setSingletonImageLoaderFactory { imageLoader }

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
						ProjectSelectionWindow { project ->
							applicationState.openProject(project)
						}
					}

					is WindowState.ProjectWindow -> {
						ProjectEditorWindow(applicationState, windowState.projectDef)
					}
				}
			}
		}
	}

	settingsUpdateJob.cancel()
	scope.cancel("Program ending")
	appScope.cancel("Program ending")
}

internal enum class ConfirmCloseResult {
	SaveAll,
	Discard,
	Cancel
}

internal fun ApplicationScope.performClose(
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

internal fun ApplicationScope.onRequestClose(
	component: AppCloseManager,
	app: ApplicationState,
	closeType: ApplicationState.CloseType
) {
	app.showConfirmProjectClose(closeType)
}
