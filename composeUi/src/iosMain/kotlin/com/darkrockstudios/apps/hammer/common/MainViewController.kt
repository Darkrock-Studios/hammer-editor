package com.darkrockstudios.apps.hammer.common

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.window.ComposeUIViewController
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.common.components.iosroot.IosRoot
import com.darkrockstudios.apps.hammer.common.compose.ProjectShortcutHost
import com.darkrockstudios.apps.hammer.common.compose.rememberKoinInject
import com.darkrockstudios.apps.hammer.common.compose.theme.AppTheme
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.globalsettings.UiTheme
import com.darkrockstudios.apps.hammer.common.projectroot.ProjectRootScaffold
import com.darkrockstudios.apps.hammer.common.projectselection.ProjectSelectScaffold
import platform.UIKit.UIViewController

// shortcutHost is owned by the Swift container: UIKit key commands are the only key hook that
// does not depend on what Compose has focused, and Kotlin cannot override that category member.
fun MainViewController(root: IosRoot, shortcutHost: ProjectShortcutHost): UIViewController =
	ComposeUIViewController {
		HammerApp(root, shortcutHost)
	}

@Composable
private fun HammerApp(root: IosRoot, shortcutHost: ProjectShortcutHost) {
	val globalSettingsStore: GlobalSettingsStore = rememberKoinInject()
	val settingsState by globalSettingsStore.globalSettingsUpdates
		.collectAsState(initial = globalSettingsStore.globalSettings)

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
					shortcutHost = shortcutHost,
				)

			null -> Unit
		}
	}
}
