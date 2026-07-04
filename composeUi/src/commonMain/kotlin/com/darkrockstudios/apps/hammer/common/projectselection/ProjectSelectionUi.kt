package com.darkrockstudios.apps.hammer.common.projectselection

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.SnackbarHost
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ExperimentalComposeApi
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.stack.animation.fade
import com.arkivanov.decompose.extensions.compose.stack.animation.predictiveback.predictiveBackAnimation
import com.arkivanov.decompose.extensions.compose.stack.animation.stackAnimation
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.common.components.projectselection.ProjectSelection
import com.darkrockstudios.apps.hammer.common.compose.SetScreenCharacteristics
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdBottomBarDestination
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdNavRailDestination
import com.darkrockstudios.apps.hammer.common.compose.rememberRootSnackbarHostState
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.projectselection.about.AboutAppUi
import com.darkrockstudios.apps.hammer.common.projectselection.settings.AccountSettingsUi
import com.darkrockstudios.apps.hammer.common.projectselection.storyideas.StoryIdeasUi

private val WIDE_SCREEN_THRESHOLD = 720.dp

fun getLocationIcon(location: ProjectSelection.Locations): ImageVector {
	return when (location) {
		ProjectSelection.Locations.Projects -> Icons.AutoMirrored.Filled.LibraryBooks
		ProjectSelection.Locations.StoryIdeas -> Icons.Filled.Lightbulb
		ProjectSelection.Locations.Settings -> Icons.Filled.Settings
		ProjectSelection.Locations.AboutApp -> Icons.Filled.Info
	}
}

@Composable
fun ProjectSelection.Locations.toHdNavRailDestination(): HdNavRailDestination<ProjectSelection.Locations> =
	HdNavRailDestination(
		id = this,
		icon = getLocationIcon(this),
		label = text.get(),
		shortLabel = shortText.get(),
	)

@Composable
fun ProjectSelection.Locations.toHdBottomBarDestination(): HdBottomBarDestination<ProjectSelection.Locations> =
	HdBottomBarDestination(
		id = this,
		icon = getLocationIcon(this),
		label = text.get(),
		shortLabel = shortText.get(),
	)

@ExperimentalMaterialApi
@ExperimentalComposeApi
@Composable
fun ProjectSelectionUi(
	component: ProjectSelection,
	modifier: Modifier = Modifier
) {
	// SetScreenCharacteristics only fires inside ProjectRootUi by default,
	// so until you open a project LocalScreenCharacteristic.current is the
	// Compact fallback regardless of window size. Set it here too so the
	// project-selection flow can branch on real width.
	SetScreenCharacteristics(WIDE_SCREEN_THRESHOLD) {
		val rootSnackbar = rememberRootSnackbarHostState()
		val stackState by component.stack.subscribeAsState()

		Box(modifier = modifier) {
			Children(
				stack = stackState,
				modifier = Modifier.fillMaxSize(),
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

					is ProjectSelection.Destination.StoryIdeasDestination -> StoryIdeasUi(
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

			UpdateAvailableDialog(component)
		}
	}
}

