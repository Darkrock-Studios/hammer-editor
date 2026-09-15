package com.darkrockstudios.apps.hammer.common.projectselection.about

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.about_logs_copy_diagnostics_button
import com.darkrockstudios.apps.hammer.about_logs_copy_diagnostics_failed
import com.darkrockstudios.apps.hammer.about_logs_copy_diagnostics_success
import com.darkrockstudios.apps.hammer.common.buildDiagnosticsReport
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineButton
import com.darkrockstudios.apps.hammer.common.compose.rememberClipboardCopier
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Puts the startup banner and the tail of the current log on the clipboard, so a bug report can
 * carry its own diagnostics without the reporter having to find, zip, and attach a log file.
 */
@Composable
fun CopyDiagnosticsButton(
	logDirectoryPath: String,
	onStatus: (String) -> Unit,
	modifier: Modifier = Modifier,
) {
	val scope = rememberCoroutineScope()
	val copyToClipboard = rememberClipboardCopier()
	var copying by remember { mutableStateOf(false) }

	val successMessage = Res.string.about_logs_copy_diagnostics_success.get()
	val failedMessage = Res.string.about_logs_copy_diagnostics_failed.get()

	HdHairlineButton(
		label = Res.string.about_logs_copy_diagnostics_button.get(),
		enabled = !copying,
		modifier = modifier,
		onClick = {
			copying = true
			scope.launch {
				val message = try {
					copyToClipboard(buildDiagnosticsReport(logDirectoryPath))
					successMessage
				} catch (e: CancellationException) {
					throw e
				} catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
					Napier.e("Failed to copy diagnostics", e)
					failedMessage
				}
				copying = false
				onStatus(message)
			}
		},
	)
}
