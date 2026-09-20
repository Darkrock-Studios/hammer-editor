package com.darkrockstudios.apps.hammer.common.projectselection.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.about_logs_directory_label
import com.darkrockstudios.apps.hammer.about_logs_export_button
import com.darkrockstudios.apps.hammer.about_logs_export_failed
import com.darkrockstudios.apps.hammer.about_logs_export_success
import com.darkrockstudios.apps.hammer.about_logs_header
import com.darkrockstudios.apps.hammer.about_logs_no_logs
import com.darkrockstudios.apps.hammer.about_logs_open_tooltip
import com.darkrockstudios.apps.hammer.common.components.projectselection.aboutapp.AboutApp
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineButton
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineSection
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMetadataItem
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.getPlatformFilesystem
import com.darkrockstudios.apps.hammer.common.platformIoDispatcher
import com.darkrockstudios.apps.hammer.common.shellPath
import com.darkrockstudios.apps.hammer.common.util.zip.zipDirectory
import io.github.aakira.napier.Napier
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.absolutePath
import io.github.vinceglb.filekit.dialogs.openFileSaver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.Path.Companion.toPath
import java.awt.Desktop
import java.io.File

private const val LOGS_ZIP_BASE_NAME = "hammer-logs"

@OptIn(ExperimentalLayoutApi::class)
@Composable
actual fun PlatformAboutSection(component: AboutApp, section: Int) {
	val scope = rememberCoroutineScope()
	val state by component.state.subscribeAsState()
	val logDir = state.logDirectoryPath
	// What the user would paste into a file manager, which under a container is not what the app writes to.
	val displayedLogDir = shellPath(logDir)
	var exporting by remember { mutableStateOf(false) }
	var status by remember { mutableStateOf<String?>(null) }

	val noLogsMessage = Res.string.about_logs_no_logs.get()
	val failedMessage = Res.string.about_logs_export_failed.get()
	val successMessage = Res.string.about_logs_export_success.get()

	HdHairlineSection(
		section = section,
		title = Res.string.about_logs_header.get(),
	) {
		Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
			HdMetadataItem(
				label = Res.string.about_logs_directory_label.get(),
				value = displayedLogDir,
				selectable = true,
			)
			FlowRow(
				horizontalArrangement = Arrangement.spacedBy(12.dp),
				verticalArrangement = Arrangement.spacedBy(12.dp),
			) {
				HdHairlineButton(
					label = Res.string.about_logs_open_tooltip.get(),
					onClick = { openLogDirectory(displayedLogDir) },
				)
				HdHairlineButton(
					label = Res.string.about_logs_export_button.get(),
					enabled = !exporting,
					onClick = {
						exporting = true
						status = null
						scope.launch {
							status = when (exportLogs(logDir)) {
								ExportResult.EXPORTED -> successMessage
								ExportResult.NO_LOGS -> noLogsMessage
								ExportResult.FAILED -> failedMessage
								ExportResult.CANCELLED -> null
							}
							exporting = false
						}
					},
				)
				CopyDiagnosticsButton(
					logDirectoryPath = logDir,
					onStatus = { status = it },
				)
			}
			status?.let {
				Text(text = it, style = MaterialTheme.typography.bodySmall)
			}
		}
	}
}

actual val platformAboutSectionCount: Int = 1

private enum class ExportResult { EXPORTED, NO_LOGS, FAILED, CANCELLED }

private suspend fun exportLogs(logDirPath: String): ExportResult {
	val fileSystem = getPlatformFilesystem()
	val logsDir = logDirPath.toPath()

	val hasLogs = withContext(platformIoDispatcher) {
		try {
			fileSystem.exists(logsDir) && fileSystem.list(logsDir).any { it.name.endsWith(".txt") }
		} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
			Napier.e("Failed to list log files", e)
			false
		}
	}
	if (!hasLogs) return ExportResult.NO_LOGS

	val destination = FileKit.openFileSaver(
		suggestedName = LOGS_ZIP_BASE_NAME,
		defaultExtension = "zip",
	) ?: return ExportResult.CANCELLED

	return withContext(platformIoDispatcher) {
		val zipPath = destination.absolutePath().toPath()
		try {
			if (fileSystem.exists(zipPath)) fileSystem.delete(zipPath)
			zipDirectory(fileSystem, logsDir, zipPath)
			ExportResult.EXPORTED
		} catch (e: CancellationException) {
			throw e
		} catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
			Napier.e("Failed to export logs", e)
			ExportResult.FAILED
		}
	}
}

/**
 * Hands the shell the nearest existing ancestor rather than a dead path. [shellPath] already
 * un-redirects the known container layouts; this covers the rest, including a log directory that
 * has not been written to yet.
 */
private fun openLogDirectory(logDir: String) {
	try {
		if (!Desktop.isDesktopSupported()) return
		val target = generateSequence(File(logDir)) { it.parentFile }
			.firstOrNull { it.isDirectory } ?: return
		Desktop.getDesktop().open(target)
	} catch (@Suppress("TooGenericExceptionCaught") e: Exception) { // Desktop.open can throw varied IO/security errors
		Napier.e("Failed to open log directory", e)
	}
}
