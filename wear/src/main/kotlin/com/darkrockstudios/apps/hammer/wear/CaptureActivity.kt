package com.darkrockstudios.apps.hammer.wear

import android.app.Activity
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.wear.input.RemoteInputIntentHelper
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.arkivanov.decompose.retainedComponent
import com.darkrockstudios.apps.hammer.common.dependencyinjection.APP_SCOPE
import com.darkrockstudios.apps.hammer.wear.components.capture.Capture
import com.darkrockstudios.apps.hammer.wear.components.capture.CaptureComponent
import com.darkrockstudios.apps.hammer.wear.ui.CaptureScreen
import com.darkrockstudios.apps.hammer.wear.ui.theme.HammerWearTheme
import org.koin.android.ext.android.get
import org.koin.core.qualifier.named

private const val EXTRA_MODE = "capture_mode"
private const val REMOTE_INPUT_KEY = "capture_text"

/** Entry point for the tile, the complication, and the app's own capture buttons. */
class CaptureActivity : ComponentActivity() {

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		val mode = runCatching { Capture.Mode.valueOf(intent.getStringExtra(EXTRA_MODE).orEmpty()) }
			.getOrDefault(Capture.Mode.Note)

		val component = retainedComponent { componentContext ->
			CaptureComponent(
				componentContext = componentContext,
				mode = mode,
				listProjects = get(),
				subscriptions = get(),
				captureUseCase = get(),
				appScope = get(named(APP_SCOPE)),
			)
		}

		setContent {
			HammerWearTheme {
				CaptureUi(component = component, onDone = ::finish)
			}
		}
	}

	companion object {
		fun intent(context: Context, mode: Capture.Mode): Intent =
			Intent(context, CaptureActivity::class.java).putExtra(EXTRA_MODE, mode.name)
	}
}

@Composable
private fun CaptureUi(component: Capture, onDone: () -> Unit) {
	val state by component.state.subscribeAsState()
	var prompted by rememberSaveable { mutableStateOf(false) }

	val label = stringResource(
		if (state.mode == Capture.Mode.Idea) R.string.capture_title_idea else R.string.capture_title_note
	)

	val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
		val data = result.data
		if (result.resultCode != Activity.RESULT_OK || data == null) return@rememberLauncherForActivityResult
		RemoteInput.getResultsFromIntent(data)
			?.getCharSequence(REMOTE_INPUT_KEY)
			?.toString()
			?.let(component::onTextEntered)
	}

	fun promptForText() {
		val intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
		RemoteInputIntentHelper.putRemoteInputsExtra(
			intent,
			listOf(RemoteInput.Builder(REMOTE_INPUT_KEY).setLabel(label).build()),
		)
		launcher.launch(intent)
	}

	// Dictation is the point of the feature, so it opens without a tap standing in the way.
	LaunchedEffect(Unit) {
		if (!prompted) {
			prompted = true
			promptForText()
		}
	}

	CaptureScreen(
		state = state,
		onEditText = ::promptForText,
		onShowProjectPicker = component::showProjectPicker,
		onSelectProject = component::selectProject,
		onSave = component::save,
		onDone = onDone,
	)
}
