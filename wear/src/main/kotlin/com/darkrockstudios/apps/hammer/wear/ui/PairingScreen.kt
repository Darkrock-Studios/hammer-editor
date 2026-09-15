package com.darkrockstudios.apps.hammer.wear.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.common.data.pairing.PairErrorCode
import com.darkrockstudios.apps.hammer.wear.R
import com.darkrockstudios.apps.hammer.wear.components.pairing.Pairing
import com.darkrockstudios.apps.hammer.wear.pairing.PairingState

@Composable
fun PairingUi(component: Pairing) {
	val state by component.state.subscribeAsState()
	PairingScreen(
		state = state,
		onRetry = component::startPairing,
		onCancel = component::cancel,
	)
}

@Composable
fun PairingScreen(
	state: PairingState,
	onRetry: () -> Unit,
	onCancel: () -> Unit,
) {
	val listState = rememberTransformingLazyColumnState()
	val waiting = state == PairingState.Idle ||
		state == PairingState.SearchingPhone ||
		state == PairingState.AwaitingConfirmation

	ScreenScaffold(scrollState = listState) { contentPadding ->
		TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
			if (waiting) {
				item {
					CircularProgressIndicator(modifier = Modifier.size(36.dp))
				}
			}
			item {
				Text(
					text = stringResource(pairingMessage(state)),
					textAlign = TextAlign.Center,
					modifier = Modifier.fillMaxWidth(),
				)
			}
			if (state == PairingState.PhoneNotFound || state is PairingState.Failed) {
				item {
					Button(
						onClick = onRetry,
						modifier = Modifier.fillMaxWidth(),
						label = { Text(stringResource(R.string.pairing_retry)) },
					)
				}
			}
			if (state != PairingState.Paired) {
				item {
					FilledTonalButton(
						onClick = onCancel,
						modifier = Modifier.fillMaxWidth(),
						label = { Text(stringResource(R.string.pairing_cancel)) },
					)
				}
			}
		}
	}
}

private fun pairingMessage(state: PairingState): Int = when (state) {
	PairingState.Idle,
	PairingState.SearchingPhone -> R.string.pairing_searching

	PairingState.AwaitingConfirmation -> R.string.pairing_awaiting
	PairingState.Paired -> R.string.pairing_paired
	PairingState.PhoneNotFound -> R.string.pairing_not_found
	is PairingState.Failed -> when (state.reason) {
		PairErrorCode.Declined -> R.string.pairing_failed_declined
		PairErrorCode.NotSignedIn -> R.string.pairing_failed_not_signed_in
		PairErrorCode.ServerRejected -> R.string.pairing_failed_server
		PairErrorCode.Unsupported -> R.string.pairing_failed_unsupported
		null -> R.string.pairing_failed
	}
}
