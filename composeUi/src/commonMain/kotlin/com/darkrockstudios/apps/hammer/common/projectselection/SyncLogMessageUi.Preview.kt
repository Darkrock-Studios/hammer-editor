package com.darkrockstudios.apps.hammer.common.projectselection

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncLogLevel
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncLogMessage
import kotlin.time.Clock

@Preview
@Composable
fun SyncLogMessageUiPreview() {
	val testMessage = SyncLogMessage(
		message = "Preview message",
		level = SyncLogLevel.INFO,
		projectName = "Test Project",
		timestamp = Clock.System.now(),
	)

	Column {
		SyncLogMessageUi(testMessage)
		SyncLogMessageUi(testMessage.copy(level = SyncLogLevel.WARN))
		SyncLogMessageUi(testMessage.copy(level = SyncLogLevel.DEBUG))
		SyncLogMessageUi(testMessage.copy(level = SyncLogLevel.ERROR))

		/////////////////////////////

		SyncLogMessageUi(
			testMessage,
			showProjectName = false
		)
		SyncLogMessageUi(
			testMessage.copy(level = SyncLogLevel.WARN),
			showProjectName = false
		)
		SyncLogMessageUi(
			testMessage.copy(level = SyncLogLevel.DEBUG),
			showProjectName = false
		)
		SyncLogMessageUi(
			testMessage.copy(level = SyncLogLevel.ERROR),
			showProjectName = false
		)
	}
}