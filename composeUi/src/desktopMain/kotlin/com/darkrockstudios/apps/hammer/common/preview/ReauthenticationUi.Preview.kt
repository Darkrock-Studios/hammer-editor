package com.darkrockstudios.apps.hammer.common.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.base.http.MfaChallenge
import com.darkrockstudios.apps.hammer.base.http.MfaMethod
import com.darkrockstudios.apps.hammer.common.components.serverreauthentication.ServerReauthentication
import com.darkrockstudios.apps.hammer.common.compose.theme.AppTheme
import com.darkrockstudios.apps.hammer.common.reauthentication.ReauthenticationContent

private fun previewState(
	password: String = "",
	working: Boolean = false,
	error: String? = null,
	mfaChallenge: MfaChallenge? = null,
) = ServerReauthentication.State(
	showReauth = true,
	serverWorking = working,
	serverUrl = "https://sync.darkrockstudios.com",
	serverEmail = "writer@example.com",
	serverPassword = password,
	serverError = error,
	mfaChallenge = mfaChallenge,
)

/**
 * Renders [ReauthenticationContent] on a scrim-like backdrop. The live re-auth dialog hangs in
 * its own [androidx.compose.ui.window.Dialog] window, so we preview the extracted body directly.
 */
@Composable
private fun ReauthPreviewScaffold(
	state: ServerReauthentication.State,
	darkTheme: Boolean,
) {
	AppTheme(globalSettingsPreview, useDarkTheme = darkTheme) {
		ReauthScrim(state, Modifier.padding(24.dp))
	}
}

@Composable
private fun ReauthScrim(state: ServerReauthentication.State, modifier: Modifier) {
	var password by remember { mutableStateOf(state.serverPassword) }
	var passwordVisible by remember { mutableStateOf(false) }

	Box(
		modifier = modifier.background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f)),
		contentAlignment = Alignment.Center,
	) {
		ReauthenticationContent(
			state = state.copy(serverPassword = password),
			passwordVisible = passwordVisible,
			onPasswordVisibleChange = { passwordVisible = it },
			onPasswordChange = { password = it },
			onClose = {},
			onLogin = {},
			onMfaCodeSubmit = {},
			onMfaCancel = {},
			modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
		)
	}
}

/** Signing back in to the sync server, over a scrim in the gallery's theme. */
@Composable
internal fun ReauthenticationScrimPreview() =
	ReauthScrim(previewState(password = "hunter2"), Modifier.fillMaxSize())

/** The MFA step of signing back in, over a scrim in the gallery's theme. */
@Composable
internal fun ReauthenticationMfaScrimPreview() = ReauthScrim(
	previewState(
		password = "hunter2",
		mfaChallenge = MfaChallenge(token = "preview", methods = listOf(MfaMethod.TOTP, MfaMethod.RECOVERY)),
	),
	Modifier.fillMaxSize(),
)

@Preview
@Composable
fun ReauthenticationPreview() {
	ReauthPreviewScaffold(previewState(), darkTheme = false)
}

@Preview
@Composable
fun ReauthenticationDarkPreview() {
	ReauthPreviewScaffold(previewState(password = "hunter2"), darkTheme = true)
}

@Preview
@Composable
fun ReauthenticationWorkingPreview() {
	ReauthPreviewScaffold(previewState(password = "hunter2", working = true), darkTheme = false)
}

@Preview
@Composable
fun ReauthenticationErrorPreview() {
	ReauthPreviewScaffold(
		previewState(error = "Invalid email or password."),
		darkTheme = false,
	)
}

@Preview
@Composable
fun ReauthenticationMfaPreview() {
	ReauthPreviewScaffold(
		previewState(
			password = "hunter2",
			mfaChallenge = MfaChallenge(token = "preview", methods = listOf(MfaMethod.TOTP, MfaMethod.RECOVERY)),
		),
		darkTheme = false,
	)
}
