package com.darkrockstudios.apps.hammer.common

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.window.ComposeUIViewController
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.common.components.iosroot.IosRoot
import com.darkrockstudios.apps.hammer.common.compose.rememberKoinInject
import com.darkrockstudios.apps.hammer.common.compose.theme.AppTheme
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsRepository
import com.darkrockstudios.apps.hammer.common.data.globalsettings.UiTheme
import com.darkrockstudios.apps.hammer.common.projectroot.ProjectRootScaffold
import com.darkrockstudios.apps.hammer.common.projectselection.ProjectSelectScaffold
import platform.UIKit.UIViewController

fun MainViewController(root: IosRoot): UIViewController = ComposeUIViewController {
	HammerApp(root)
}

@Composable
private fun HammerApp(root: IosRoot) {
	val globalSettingsRepository: GlobalSettingsRepository = rememberKoinInject()
	val settingsState by globalSettingsRepository.globalSettingsUpdates
		.collectAsState(initial = globalSettingsRepository.globalSettings)

	val systemDark = isSystemInDarkTheme()
	val isDark = when (settingsState.uiTheme) {
		UiTheme.Light -> false
		UiTheme.Dark -> true
		UiTheme.FollowSystem -> systemDark
	}

	AppTheme(settings = settingsState, useDarkTheme = isDark) {
		val slot by root.slot.subscribeAsState()
		when (val destination = slot.child?.instance) {
			is IosRoot.Destination.ProjectSelectDestination ->
				ProjectSelectScaffold(destination.component)

			is IosRoot.Destination.ProjectRootDestination ->
				ProjectRootScaffold(
					component = destination.component,
					onCloseRequest = { root.closeProject() },
				)

			null -> Unit
		}
	}
}
