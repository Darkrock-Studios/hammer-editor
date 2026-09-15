package com.darkrockstudios.apps.hammer.wear.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.darkrockstudios.apps.hammer.wear.R

@Composable
fun OnboardingScreen(
	onPair: () -> Unit,
	onManualSignIn: () -> Unit,
) {
	val listState = rememberTransformingLazyColumnState()
	ScreenScaffold(scrollState = listState) { contentPadding ->
		TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
			item {
				ListHeader { Text(stringResource(R.string.app_name)) }
			}
			item {
				Text(
					text = stringResource(R.string.onboarding_body),
					textAlign = TextAlign.Center,
					modifier = Modifier.fillMaxWidth(),
				)
			}
			item {
				Button(
					onClick = onPair,
					modifier = Modifier.fillMaxWidth(),
					label = { Text(stringResource(R.string.onboarding_pair)) },
				)
			}
			item {
				FilledTonalButton(
					onClick = onManualSignIn,
					modifier = Modifier.fillMaxWidth(),
					label = { Text(stringResource(R.string.onboarding_manual_sign_in)) },
				)
			}
		}
	}
}
