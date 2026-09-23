package com.darkrockstudios.apps.hammer.wear.ui

import android.app.Activity
import android.app.RemoteInput
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.input.RemoteInputIntentHelper
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.wear.R
import com.darkrockstudios.apps.hammer.wear.components.signin.ManualSignIn

private enum class SignInField { Server, Email, Password }

private const val REMOTE_INPUT_KEY = "sign_in_field"

@Composable
fun ManualSignInUi(component: ManualSignIn) {
	val state by component.state.subscribeAsState()
	var editing by remember { mutableStateOf<SignInField?>(null) }

	val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
		val field = editing
		editing = null
		val data = result.data
		if (result.resultCode != Activity.RESULT_OK || data == null || field == null) return@rememberLauncherForActivityResult
		val text = RemoteInput.getResultsFromIntent(data)?.getCharSequence(REMOTE_INPUT_KEY)?.toString()
			?: return@rememberLauncherForActivityResult
		when (field) {
			SignInField.Server -> component.updateServer(text)
			SignInField.Email -> component.updateEmail(text)
			SignInField.Password -> component.updatePassword(text)
		}
	}

	val requestLocalNetwork = rememberLocalNetworkPermissionRequest(component::onLocalNetworkPermissionResult)
	// Every attempt asks, since each one is an explicit tap on Sign in.
	LaunchedEffect(state.localNetworkBlocked) {
		if (state.localNetworkBlocked) requestLocalNetwork()
	}

	val labels = mapOf(
		SignInField.Server to stringResource(R.string.sign_in_server),
		SignInField.Email to stringResource(R.string.sign_in_email),
		SignInField.Password to stringResource(R.string.sign_in_password),
	)

	ManualSignInScreen(
		state = state,
		onEditField = { field ->
			editing = field
			val intent: Intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
			RemoteInputIntentHelper.putRemoteInputsExtra(
				intent,
				listOf(RemoteInput.Builder(REMOTE_INPUT_KEY).setLabel(labels.getValue(field)).build()),
			)
			launcher.launch(intent)
		},
		onSignIn = component::signIn,
	)
}

@Composable
private fun ManualSignInScreen(
	state: ManualSignIn.State,
	onEditField: (SignInField) -> Unit,
	onSignIn: () -> Unit,
) {
	ManualSignInContent(
		state = state,
		onEditServer = { onEditField(SignInField.Server) },
		onEditEmail = { onEditField(SignInField.Email) },
		onEditPassword = { onEditField(SignInField.Password) },
		onSignIn = onSignIn,
	)
}

@Composable
fun ManualSignInContent(
	state: ManualSignIn.State,
	onEditServer: () -> Unit,
	onEditEmail: () -> Unit,
	onEditPassword: () -> Unit,
	onSignIn: () -> Unit,
) {
	val listState = rememberTransformingLazyColumnState()
	val spec = rememberTransformationSpec()
	val notSet = stringResource(R.string.sign_in_not_set)

	ScreenScaffold(
		scrollState = listState,
		edgeButton = {
			EdgeButton(onClick = onSignIn, enabled = !state.busy) {
				if (state.busy) {
					CircularProgressIndicator(modifier = Modifier.size(24.dp))
				} else {
					Text(stringResource(R.string.sign_in_button))
				}
			}
		},
	) { contentPadding ->
		TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
			item {
				ListHeader(modifier = Modifier.listHeader(this, spec)) { Text(stringResource(R.string.sign_in_title)) }
			}
			item {
				SignInFieldButton(
					spec = spec,
					label = stringResource(R.string.sign_in_server),
					value = state.server.ifBlank { notSet },
					onClick = onEditServer,
				)
			}
			if (state.serverInsecure) {
				item {
					Text(
						text = stringResource(R.string.sign_in_insecure_warning),
						color = MaterialTheme.colorScheme.error,
						textAlign = TextAlign.Center,
						modifier = Modifier.listText(this, spec),
					)
				}
			}
			item {
				SignInFieldButton(
					spec = spec,
					label = stringResource(R.string.sign_in_email),
					value = state.email.ifBlank { notSet },
					onClick = onEditEmail,
				)
			}
			item {
				SignInFieldButton(
					spec = spec,
					label = stringResource(R.string.sign_in_password),
					value = if (state.hasPassword) "••••••" else notSet,
					onClick = onEditPassword,
				)
			}
			val error = state.error
			if (error != null) {
				item {
					Text(
						text = when (error) {
							ManualSignIn.SignInError.MissingFields -> stringResource(R.string.sign_in_error_missing)
							ManualSignIn.SignInError.TermsRequired -> stringResource(R.string.sign_in_error_terms)
							ManualSignIn.SignInError.LocalNetworkDenied ->
								stringResource(R.string.sign_in_error_local_network)
							is ManualSignIn.SignInError.Message ->
								error.text ?: stringResource(R.string.sign_in_error_generic)
						},
						color = MaterialTheme.colorScheme.error,
						textAlign = TextAlign.Center,
						modifier = Modifier.listText(this, spec),
					)
				}
			}
		}
	}
}

@Composable
private fun TransformingLazyColumnItemScope.SignInFieldButton(
	spec: TransformationSpec,
	label: String,
	value: String,
	onClick: () -> Unit,
) {
	FilledTonalButton(
		onClick = onClick,
		modifier = Modifier.listButton(this, spec),
		transformation = SurfaceTransformation(spec),
		label = { Text(label) },
		secondaryLabel = { Text(value, maxLines = 1) },
	)
}
