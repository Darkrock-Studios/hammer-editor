package com.darkrockstudios.apps.hammer.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ExperimentalComposeApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.decompose.ExperimentalDecomposeApi
import com.arkivanov.decompose.extensions.compose.jetbrains.lifecycle.LifecycleController
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.darkrockstudios.apps.hammer.base.BuildMetadata
import com.darkrockstudios.apps.hammer.common.components.projectselection.ProjectSelection
import com.darkrockstudios.apps.hammer.common.components.projectselection.ProjectSelectionComponent
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.moko.get
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.projectselection.ProjectSelectionUi
import com.darkrockstudios.apps.hammer.common.projectselection.getLocationIcon

@ExperimentalMaterialApi
@ExperimentalComposeApi
@ExperimentalDecomposeApi
@Composable
internal fun ApplicationScope.ProjectSelectionWindow(
	onProjectSelected: (projectDef: ProjectDef) -> Unit
) {
	val lifecycle = remember { LifecycleRegistry() }
	val compContext = remember { DefaultComponentContext(lifecycle) }
	val windowState = rememberWindowState()
	val component = remember {
		ProjectSelectionComponent(
			componentContext = compContext,
			showProjectDirectory = true,
			onProjectSelected = onProjectSelected
		)
	}
	LifecycleController(lifecycle, windowState)

	Window(
		title = DR.strings.account_window_title.get(),
		state = windowState,
		onCloseRequest = ::exitApplication,
		icon = painterResource("icon.png"),
	) {
		val slot by component.slot.subscribeAsState()

		Row(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
			NavigationRail(modifier = Modifier.padding(top = Ui.Padding.M)) {
				ProjectSelection.Locations.values().forEach { item ->
					NavigationRailItem(
						icon = { Icon(imageVector = getLocationIcon(item), contentDescription = item.text) },
						label = { Text(item.text) },
						selected = item == slot.child?.configuration?.location,
						onClick = { component.showLocation(item) }
					)
				}

				Spacer(modifier = Modifier.weight(1f))

				Text(
					"v${BuildMetadata.APP_VERSION}",
					style = MaterialTheme.typography.labelSmall,
					fontWeight = FontWeight.Thin,
				)
			}

			ProjectSelectionUi(component)
		}
	}
}