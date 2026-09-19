package com.darkrockstudios.apps.hammer.wear.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.SwipeToDismissBox
import androidx.wear.compose.material3.TimeText
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.wear.components.WearRoot

@Composable
fun WearRootUi(component: WearRoot) {
	val stack by component.stack.subscribeAsState()
	val saveableStateHolder = rememberSaveableStateHolder()

	AppScaffold(timeText = { TimeText() }) {
		val active = stack.active
		val previous = stack.backStack.lastOrNull()

		SwipeToDismissBox(
			onDismissed = component::onBack,
			backgroundKey = previous?.configuration ?: Unit,
			contentKey = active.configuration,
			userSwipeEnabled = previous != null,
		) { isBackground: Boolean ->
			val child = if (isBackground) previous else active
			if (child != null) {
				saveableStateHolder.SaveableStateProvider(child.configuration.toString()) {
					WearDestinationUi(child.instance, component)
				}
			}
		}
	}
}

@Composable
private fun WearDestinationUi(destination: WearRoot.Destination, root: WearRoot) {
	when (destination) {
		WearRoot.Destination.Onboarding -> OnboardingScreen(
			onPair = root::showPairing,
			onManualSignIn = root::showManualSignIn,
		)

		is WearRoot.Destination.PairingDestination -> PairingUi(destination.component)
		is WearRoot.Destination.ManualSignInDestination -> ManualSignInUi(destination.component)
		is WearRoot.Destination.ProjectsDestination -> WearProjectsUi(destination.component)
		is WearRoot.Destination.SyncLogDestination -> SyncLogUi(destination.component)
	}
}
