package com.darkrockstudios.apps.hammer.common.projectselection

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ExperimentalComposeApi
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.stack.animation.fade
import com.arkivanov.decompose.extensions.compose.stack.animation.predictiveback.predictiveBackAnimation
import com.arkivanov.decompose.extensions.compose.stack.animation.stackAnimation
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.MR
import com.darkrockstudios.apps.hammer.common.components.projectselection.ProjectSelection
import com.darkrockstudios.apps.hammer.common.compose.moko.get
import com.darkrockstudios.apps.hammer.common.compose.rememberRootSnackbarHostState
import com.darkrockstudios.apps.hammer.common.projectselection.settings.AccountSettingsUi

fun getLocationIcon(location: ProjectSelection.Locations): ImageVector {
	return when (location) {
		ProjectSelection.Locations.Projects -> Icons.Filled.LibraryBooks
		ProjectSelection.Locations.Settings -> Icons.Filled.Settings
		ProjectSelection.Locations.AboutApp -> Icons.Filled.Info
	}
}

@ExperimentalMaterialApi
@ExperimentalComposeApi
@Composable
fun ProjectSelectionUi(
	component: ProjectSelection,
	modifier: Modifier = Modifier
) {
	val rootSnackbar = rememberRootSnackbarHostState()
	val stackState by component.stack.subscribeAsState()

	Box {
		Children(
			stack = stackState,
			modifier = modifier,
			animation = predictiveBackAnimation(
				backHandler = component.backHandler,
				fallbackAnimation = stackAnimation { _ -> fade() },
				onBack = component::onBack,
			),
		) { child ->
			when (val destination = child.instance) {
				is ProjectSelection.Destination.AccountSettingsDestination -> AccountSettingsUi(
					destination.component,
					rootSnackbar,
				)

				is ProjectSelection.Destination.ProjectsListDestination -> ProjectListUi(
					destination.component,
					rootSnackbar,
				)

				is ProjectSelection.Destination.AboutAppDestination -> AboutAppUi(
					destination.component,
				)
			}
		}

		SnackbarHost(
			rootSnackbar.snackbarHostState,
			modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter)
		)
	}
}

@ExperimentalMaterialApi
@ExperimentalComposeApi
@Composable
fun ProjectSelectionFab(
	component: ProjectSelection,
	modifier: Modifier = Modifier,
) {
	val stackState by component.stack.subscribeAsState()

	when (val destination = stackState.active.instance) {
		is ProjectSelection.Destination.AccountSettingsDestination -> {
			/* none */
		}

		is ProjectSelection.Destination.ProjectsListDestination -> {
			FloatingActionButton(
				modifier = modifier,
				onClick = { destination.component.showCreate() }
			) {
				Icon(
					imageVector = Icons.Filled.Create,
					MR.strings.projects_list_create_button.get()
				)
			}
		}

		is ProjectSelection.Destination.AboutAppDestination -> {
			/* none */
		}
	}
}