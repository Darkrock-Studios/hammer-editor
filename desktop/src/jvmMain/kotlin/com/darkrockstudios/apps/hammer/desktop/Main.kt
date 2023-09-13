package com.darkrockstudios.apps.hammer.desktop

import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.runtime.*
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.application
import com.arkivanov.decompose.ExperimentalDecomposeApi
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.getAndUpdate
import com.darkrockstudios.apps.hammer.common.AppCloseManager
import com.darkrockstudios.apps.hammer.common.compose.getDefaultDispatcher
import com.darkrockstudios.apps.hammer.common.compose.getMainDispatcher
import com.darkrockstudios.apps.hammer.common.compose.theme.AppTheme
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsRepository
import com.darkrockstudios.apps.hammer.common.data.globalsettings.UiTheme
import com.darkrockstudios.apps.hammer.common.dependencyinjection.NapierLogger
import com.darkrockstudios.apps.hammer.common.dependencyinjection.imageLoadingModule
import com.darkrockstudios.apps.hammer.common.dependencyinjection.mainModule
import com.darkrockstudios.apps.hammer.common.setInDevelopmentMode
import com.darkrockstudios.apps.hammer.desktop.aboutlibraries.aboutLibrariesModule
import com.github.weisj.darklaf.LafManager
import com.github.weisj.darklaf.theme.DarculaTheme
import com.github.weisj.darklaf.theme.IntelliJTheme
import com.jthemedetecor.OsThemeDetector
import com.seiko.imageloader.ImageLoader
import com.seiko.imageloader.LocalImageLoader
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.coroutines.*
import org.koin.core.context.GlobalContext
import org.koin.java.KoinJavaComponent.get
import org.koin.java.KoinJavaComponent.getKoin
import java.util.logging.ConsoleHandler

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

@ExperimentalDecomposeApi
@ExperimentalMaterialApi
@ExperimentalComposeApi
fun main(args: Array<String>) {
	handleArguments(args)

	val appScope = CoroutineScope(Dispatchers.Default)
	Napier.base(DebugAntilog(handler = listOf(FileLogger(scope = appScope), ConsoleHandler())))

	GlobalContext.startKoin {
		logger(NapierLogger())
		modules(mainModule, imageLoadingModule, aboutLibrariesModule)
	}

	val scope = CoroutineScope(getDefaultDispatcher())
	val mainDispatcher = getMainDispatcher()

	val osThemeDetector = OsThemeDetector.getDetector()
	if (osThemeDetector.isDark) {
		LafManager.install(DarculaTheme())
	} else {
		LafManager.install(IntelliJTheme())
	}

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
		val imageLoader: ImageLoader = get(ImageLoader::class.java)

		val settingsState by globalSettings.subscribeAsState()
		val initialDark = when (settingsState.uiTheme) {
			UiTheme.Light -> false
			UiTheme.Dark -> true
			UiTheme.FollowSystem -> osThemeDetector.isDark
		}
		var darkMode by remember(initialDark) { mutableStateOf(initialDark) }
		val themeListener = remember {
			{ isDarkModeEnabled: Boolean ->
				darkMode = isDarkModeEnabled

				if (darkMode) {
					LafManager.install(DarculaTheme())
				} else {
					LafManager.install(IntelliJTheme())
				}
				LafManager.updateLaf()
			}
		}
		if (settingsState.uiTheme == UiTheme.FollowSystem) {
			osThemeDetector.registerListener(themeListener)
		} else {
			osThemeDetector.registerListener(themeListener)
		}

		AppTheme(useDarkTheme = darkMode) {
			CompositionLocalProvider(
				LocalImageLoader provides imageLoader,
			) {
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
		ApplicationState.CloseType.Application -> exitApplication()
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
