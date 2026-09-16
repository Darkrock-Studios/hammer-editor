package com.darkrockstudios.apps.hammer.wear.ui.preview

import androidx.compose.runtime.Composable
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import com.darkrockstudios.apps.hammer.common.data.pairing.PairErrorCode
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.ProjectSyncOutcome
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncLogLevel
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncLogMessage
import com.darkrockstudios.apps.hammer.wear.components.capture.Capture
import com.darkrockstudios.apps.hammer.wear.components.projects.WearProjects
import com.darkrockstudios.apps.hammer.wear.components.signin.ManualSignIn
import com.darkrockstudios.apps.hammer.wear.components.synclog.SyncLog
import com.darkrockstudios.apps.hammer.wear.pairing.PairingState
import com.darkrockstudios.apps.hammer.wear.ui.CaptureScreen
import com.darkrockstudios.apps.hammer.wear.ui.ManualSignInContent
import com.darkrockstudios.apps.hammer.wear.ui.OnboardingScreen
import com.darkrockstudios.apps.hammer.wear.ui.PairingScreen
import com.darkrockstudios.apps.hammer.wear.ui.SyncLogScreen
import com.darkrockstudios.apps.hammer.wear.ui.WearProjectsScreen
import com.darkrockstudios.apps.hammer.wear.ui.theme.HammerWearTheme
import kotlin.time.Instant

@Composable
private fun WearPreviewFrame(content: @Composable () -> Unit) {
	HammerWearTheme {
		AppScaffold { content() }
	}
}

@WearPreviewDevices
@Composable
private fun OnboardingPreview() {
	WearPreviewFrame { OnboardingScreen(onPair = {}, onManualSignIn = {}) }
}

@WearPreviewDevices
@Composable
private fun PairingAwaitingPreview() {
	WearPreviewFrame {
		PairingScreen(state = PairingState.AwaitingConfirmation, onRetry = {}, onCancel = {})
	}
}

@WearPreviewDevices
@Composable
private fun PairingDeclinedPreview() {
	WearPreviewFrame {
		PairingScreen(state = PairingState.Failed(PairErrorCode.Declined), onRetry = {}, onCancel = {})
	}
}

@WearPreviewDevices
@Composable
private fun PairingPhoneNotFoundPreview() {
	WearPreviewFrame {
		PairingScreen(state = PairingState.PhoneNotFound, onRetry = {}, onCancel = {})
	}
}

@WearPreviewDevices
@Composable
private fun ManualSignInEmptyPreview() {
	WearPreviewFrame {
		ManualSignInContent(
			state = ManualSignIn.State(),
			onEditServer = {},
			onEditEmail = {},
			onEditPassword = {},
			onSignIn = {},
		)
	}
}

@WearPreviewDevices
@Composable
private fun ManualSignInInsecurePreview() {
	WearPreviewFrame {
		ManualSignInContent(
			state = ManualSignIn.State(
				server = "http://192.168.1.50:8080",
				serverInsecure = true,
				email = "writer@example.com",
				hasPassword = true,
			),
			onEditServer = {},
			onEditEmail = {},
			onEditPassword = {},
			onSignIn = {},
		)
	}
}

@WearPreviewDevices
@Composable
private fun ManualSignInErrorPreview() {
	WearPreviewFrame {
		ManualSignInContent(
			state = ManualSignIn.State(
				server = "hammer.ink",
				email = "writer@example.com",
				hasPassword = true,
				error = ManualSignIn.SignInError.Message("Invalid credentials"),
			),
			onEditServer = {},
			onEditEmail = {},
			onEditPassword = {},
			onSignIn = {},
		)
	}
}

@WearPreviewDevices
@Composable
private fun ManualSignInBusyPreview() {
	WearPreviewFrame {
		ManualSignInContent(
			state = ManualSignIn.State(server = "hammer.ink", email = "writer@example.com", hasPassword = true, busy = true),
			onEditServer = {},
			onEditEmail = {},
			onEditPassword = {},
			onSignIn = {},
		)
	}
}

private val previewRows = listOf(
	WearProjects.ProjectRow(name = "The Lighthouse", canSubscribe = true, subscribed = true, outcome = ProjectSyncOutcome.Success),
	WearProjects.ProjectRow(name = "Salt Roads", canSubscribe = true, subscribed = true, progress = 0.4f),
	WearProjects.ProjectRow(name = "Short Stories", canSubscribe = true, subscribed = false),
	WearProjects.ProjectRow(name = "New Draft", canSubscribe = false, subscribed = false),
)

@Composable
private fun ProjectsPreview(state: WearProjects.State) {
	WearPreviewFrame {
		WearProjectsScreen(
			state = state,
			onNewNote = {},
			onNewIdea = {},
			onToggleSubscription = {},
			onSyncNow = {},
			onShowSyncLog = {},
			onSignOut = {},
			onConfirmSignOut = {},
			onCancelSignOut = {},
			onDismissNotice = {},
		)
	}
}

@WearPreviewDevices
@Composable
private fun ProjectsSyncingPreview() {
	ProjectsPreview(
		WearProjects.State(
			accountEmail = "writer@example.com",
			projects = previewRows,
			loaded = true,
			syncing = true,
		)
	)
}

@WearPreviewDevices
@Composable
private fun ProjectsNeedsReauthPreview() {
	ProjectsPreview(
		WearProjects.State(
			accountEmail = "writer@example.com",
			projects = previewRows.take(1).map { it.copy(outcome = ProjectSyncOutcome.Failed) },
			loaded = true,
			needsReauth = true,
		)
	)
}

@WearPreviewDevices
@Composable
private fun ProjectsEmptyPreview() {
	ProjectsPreview(WearProjects.State(accountEmail = "writer@example.com", loaded = true))
}

@WearPreviewDevices
@Composable
private fun ProjectsUnsyncedKeptPreview() {
	ProjectsPreview(
		WearProjects.State(
			accountEmail = "writer@example.com",
			projects = previewRows.take(2).map { it.copy(unsubscribing = true) },
			loaded = true,
			notice = WearProjects.Notice("Salt Roads", WearProjects.Notice.Reason.UnsyncedKept),
		)
	)
}

@WearPreviewDevices
@Composable
private fun ProjectsUnsubscribeFailedPreview() {
	ProjectsPreview(
		WearProjects.State(
			accountEmail = "writer@example.com",
			projects = previewRows,
			loaded = true,
			notice = WearProjects.Notice("Salt Roads", WearProjects.Notice.Reason.Failed),
		)
	)
}

@WearPreviewDevices
@Composable
private fun ProjectsCheckingSignOutPreview() {
	ProjectsPreview(
		WearProjects.State(
			accountEmail = "writer@example.com",
			projects = previewRows,
			loaded = true,
			checkingSignOut = true,
		)
	)
}

@WearPreviewDevices
@Composable
private fun ProjectsSignOutWarningPreview() {
	ProjectsPreview(
		WearProjects.State(
			accountEmail = "writer@example.com",
			projects = previewRows,
			loaded = true,
			signOutWarning = WearProjects.SignOutWarning(items = 3),
		)
	)
}

@WearPreviewDevices
@Composable
private fun ProjectsSignOutWarningUnknownPreview() {
	ProjectsPreview(
		WearProjects.State(
			accountEmail = "writer@example.com",
			projects = previewRows,
			loaded = true,
			signOutWarning = WearProjects.SignOutWarning(items = 0),
		)
	)
}

@Composable
private fun CapturePreview(state: Capture.State) {
	WearPreviewFrame {
		CaptureScreen(
			state = state,
			onEditText = {},
			onShowProjectPicker = {},
			onSelectProject = {},
			onDismissProjectPicker = {},
			onSave = {},
			onDone = {},
		)
	}
}

private val previewCaptureProjects = listOf("The Lighthouse", "Salt Roads")

@WearPreviewDevices
@Composable
private fun CaptureNoteEmptyPreview() {
	CapturePreview(
		Capture.State(
			mode = Capture.Mode.Note,
			projects = previewCaptureProjects,
			projectName = "The Lighthouse",
			loading = false,
		)
	)
}

@WearPreviewDevices
@Composable
private fun CaptureNoteDictatedPreview() {
	CapturePreview(
		Capture.State(
			mode = Capture.Mode.Note,
			text = "The keeper never lights the lamp on the night his brother visits",
			projects = previewCaptureProjects,
			projectName = "The Lighthouse",
			loading = false,
		)
	)
}

@WearPreviewDevices
@Composable
private fun CaptureIdeaPreview() {
	CapturePreview(
		Capture.State(
			mode = Capture.Mode.Idea,
			text = "A town where everyone shares one memory",
			loading = false,
		)
	)
}

@WearPreviewDevices
@Composable
private fun CaptureProjectPickerPreview() {
	CapturePreview(
		Capture.State(
			mode = Capture.Mode.Note,
			text = "Salt on the stairs",
			projects = previewCaptureProjects,
			projectName = "The Lighthouse",
			pickingProject = true,
			loading = false,
		)
	)
}

@WearPreviewDevices
@Composable
private fun CaptureSavedPreview() {
	CapturePreview(
		Capture.State(
			mode = Capture.Mode.Note,
			text = "Salt on the stairs",
			projectName = "The Lighthouse",
			loading = false,
			outcome = Capture.Outcome.Saved(pending = 3),
		)
	)
}

@WearPreviewDevices
@Composable
private fun CaptureSavedUncountedPreview() {
	CapturePreview(
		Capture.State(
			mode = Capture.Mode.Idea,
			text = "A town where everyone shares one memory",
			loading = false,
			outcome = Capture.Outcome.Saved(),
		)
	)
}

@WearPreviewDevices
@Composable
private fun CaptureNoProjectsPreview() {
	CapturePreview(
		Capture.State(mode = Capture.Mode.Note, loading = false, outcome = Capture.Outcome.NoProjects)
	)
}

@WearPreviewDevices
@Composable
private fun CaptureFailedPreview() {
	CapturePreview(
		Capture.State(mode = Capture.Mode.Note, loading = false, outcome = Capture.Outcome.Failed)
	)
}

@WearPreviewDevices
@Composable
private fun SyncLogPreview() {
	val now = Instant.fromEpochSeconds(1_800_000_000)
	WearPreviewFrame {
		SyncLogScreen(
			SyncLog.State(
				entries = listOf(
					SyncLogMessage("Sync failed: timeout", SyncLogLevel.ERROR, "Salt Roads", now),
					SyncLogMessage("No changes, skipped", SyncLogLevel.INFO, "The Lighthouse", now),
					SyncLogMessage("Syncing Account...", SyncLogLevel.INFO, null, now),
				)
			)
		)
	}
}

@WearPreviewDevices
@Composable
private fun SyncLogEmptyPreview() {
	WearPreviewFrame { SyncLogScreen(SyncLog.State()) }
}
