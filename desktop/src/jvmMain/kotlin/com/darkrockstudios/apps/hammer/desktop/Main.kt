package com.darkrockstudios.apps.hammer.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.AlertDialog
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.application
import com.arkivanov.decompose.ExperimentalDecomposeApi
import com.darkrockstudios.apps.hammer.common.AppCloseManager
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.theme.AppTheme
import com.darkrockstudios.apps.hammer.common.dependencyinjection.NapierLogger
import com.darkrockstudios.apps.hammer.common.dependencyinjection.imageLoadingModule
import com.darkrockstudios.apps.hammer.common.dependencyinjection.mainModule
import com.jthemedetecor.OsThemeDetector
import com.seiko.imageloader.ImageLoader
import com.seiko.imageloader.LocalImageLoader
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import org.koin.core.context.GlobalContext
import org.koin.java.KoinJavaComponent.get
import javax.swing.UIManager

@ExperimentalDecomposeApi
@ExperimentalMaterialApi
@ExperimentalComposeApi
fun main() {
	Napier.base(DebugAntilog())

	UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())

	GlobalContext.startKoin {
		logger(NapierLogger())
		modules(mainModule, imageLoadingModule)
	}

	val osThemeDetector = OsThemeDetector.getDetector()
	application {
		val applicationState = remember { ApplicationState() }
		val imageLoader: ImageLoader = get(ImageLoader::class.java)

		var darkMode by remember { mutableStateOf(osThemeDetector.isDark) }
		osThemeDetector.registerListener { isDarkModeEnabled ->
			darkMode = isDarkModeEnabled
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
}

@ExperimentalMaterialApi
@ExperimentalComposeApi
@Composable
internal fun confirmCloseDialog(
	closeType: ApplicationState.CloseType,
	dismissDialog: (ConfirmCloseResult, ApplicationState.CloseType) -> Unit
) {
	AlertDialog(
		title = { Text("Unsaved Scenes") },
		text = { Text("Save unsaved scenes?") },
		onDismissRequest = { /* Noop */ },
		buttons = {
			Column(
				modifier = Modifier.fillMaxWidth(),
			) {
				Button(onClick = { dismissDialog(ConfirmCloseResult.SaveAll, closeType) }) {
					Text("Save and close")
				}
				Button(onClick = {
					dismissDialog(
						ConfirmCloseResult.Cancel,
						ApplicationState.CloseType.None
					)
				}) {
					Text("Cancel")
				}
				Button(onClick = { dismissDialog(ConfirmCloseResult.Discard, closeType) }) {
					Text("Discard and close")
				}
			}
		},
		modifier = Modifier.width(300.dp).padding(Ui.Padding.XL)
	)
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
	if (component.hasUnsavedBuffers()) {
		app.showConfirmProjectClose(closeType)
	} else {
		performClose(app, closeType)
	}
}
