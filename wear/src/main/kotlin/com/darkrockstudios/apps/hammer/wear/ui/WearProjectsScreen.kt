package com.darkrockstudios.apps.hammer.wear.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.wear.R
import com.darkrockstudios.apps.hammer.wear.components.projects.WearProjects

@Composable
fun WearProjectsUi(component: WearProjects) {
	val state by component.state.subscribeAsState()
	WearProjectsScreen(state = state, onSignOut = component::signOut)
}

@Composable
fun WearProjectsScreen(
	state: WearProjects.State,
	onSignOut: () -> Unit,
) {
	val listState = rememberTransformingLazyColumnState()
	ScreenScaffold(scrollState = listState) { contentPadding ->
		TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
			item {
				ListHeader { Text(stringResource(R.string.app_name)) }
			}
			state.accountEmail?.let { email ->
				item {
					Text(
						text = stringResource(R.string.account_signed_in_as, email),
						textAlign = TextAlign.Center,
						modifier = Modifier.fillMaxWidth(),
					)
				}
			}
			item {
				FilledTonalButton(
					onClick = onSignOut,
					modifier = Modifier.fillMaxWidth(),
					label = { Text(stringResource(R.string.account_sign_out)) },
				)
			}
		}
	}
}
