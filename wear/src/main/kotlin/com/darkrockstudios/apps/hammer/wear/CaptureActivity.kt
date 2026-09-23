package com.darkrockstudios.apps.hammer.wear

import android.app.Activity
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.SwipeToDismissBox
import androidx.wear.compose.material3.TimeText
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

private const val REMOTE_INPUT_KEY = "capture_text"

/** Entry point for the tile, the complication, and the app's own capture buttons. */
class CaptureActivity : ComponentActivity() {

	override fun onCreate(savedInstanceState: Bundle?) {
		installSplashScreen()
		super.onCreate(savedInstanceState)

		val mode = runCatching { Capture.Mode.valueOf(intent.getStringExtra(EXTRA_MODE).orEmpty()) }
			.getOrDefault(Capture.Mode.Note)

		val pickProject = intent.getStringExtra(EXTRA_PICK_PROJECT).toBoolean()

		val component = retainedComponent { componentContext ->
			CaptureComponent(
				componentContext = componentContext,
				mode = mode,
				startWithPicker = pickProject,
				captureTargets = get(),
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
		/** Read by the tile, which builds its launch action rather than an Intent. */
		const val EXTRA_MODE = "capture_mode"

		/** Lets the tile's project name act as "change this", which it cannot do itself. */
		const val EXTRA_PICK_PROJECT = "capture_pick_project"

		fun intent(context: Context, mode: Capture.Mode, pickProject: Boolean = false): Intent =
			Intent(context, CaptureActivity::class.java)
				.putExtra(EXTRA_MODE, mode.name)
				.putExtra(EXTRA_PICK_PROJECT, pickProject.toString())
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

	// Dictation is the point of the feature, so it opens without a tap standing in the way. It waits
	// for the component to settle first: prompting before we know there is a project to save to
	// would take a whole dictated note and then throw it away on the "no project" screen.
	LaunchedEffect(state.loading, state.outcome, state.pickingProject) {
		if (!prompted && !state.loading && state.outcome == null && !state.pickingProject) {
			prompted = true
			promptForText()
		}
	}

	// One way out, whether it came from the swipe or the back button. Leaving is only immediate
	// when there is nothing to lose.
	fun exit() {
		when {
			state.confirmingDiscard -> component.cancelDiscard()
			state.pickingProject -> component.dismissProjectPicker()
			state.hasUnsavedWork -> component.requestDiscard()
			else -> onDone()
		}
	}

	val layered = state.pickingProject || state.confirmingDiscard
	BackHandler(enabled = layered || state.hasUnsavedWork) { exit() }

	AppScaffold(timeText = { TimeText() }) {
		SwipeToDismissBox(
			onDismissed = ::exit,
			backgroundKey = false,
			contentKey = layered,
			// Disabled only when the gesture can safely fall through to finishing the activity.
			userSwipeEnabled = layered || state.hasUnsavedWork,
		) { isBackground ->
			CaptureScreen(
				state = if (isBackground) {
					state.copy(pickingProject = false, confirmingDiscard = false)
				} else {
					state
				},
				onEditText = ::promptForText,
				onShowProjectPicker = component::showProjectPicker,
				onSelectProject = component::selectProject,
				onDismissProjectPicker = component::dismissProjectPicker,
				onCancelDiscard = component::cancelDiscard,
				onDiscard = onDone,
				onSave = component::save,
				onDone = onDone,
			)
		}
	}
}
