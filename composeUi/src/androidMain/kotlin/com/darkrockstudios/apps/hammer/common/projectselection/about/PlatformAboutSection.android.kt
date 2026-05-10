package com.darkrockstudios.apps.hammer.common.projectselection.about

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.about_logs_export_button
import com.darkrockstudios.apps.hammer.about_logs_header
import com.darkrockstudios.apps.hammer.about_logs_no_logs
import com.darkrockstudios.apps.hammer.common.components.projectselection.aboutapp.AboutApp
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineButton
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineSection
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@Composable
actual fun PlatformAboutSection(component: AboutApp, section: Int) {
	val context = LocalContext.current
	val scope = rememberCoroutineScope()
	val state by component.state.subscribeAsState()
	var exporting by remember { mutableStateOf(false) }
	val noLogsMessage = Res.string.about_logs_no_logs.get()

	HdHairlineSection(
		section = section,
		title = Res.string.about_logs_header.get(),
	) {
		HdHairlineButton(
			label = Res.string.about_logs_export_button.get(),
			onClick = {
				if (exporting) return@HdHairlineButton
				exporting = true
				scope.launch {
					val success = exportAndShareLogs(context, state.logDirectoryPath)
					if (!success) {
						Toast.makeText(context, noLogsMessage, Toast.LENGTH_SHORT).show()
					}
					exporting = false
				}
			},
		)
	}
}

actual val platformAboutSectionCount: Int = 1

private suspend fun exportAndShareLogs(context: Context, logDirPath: String): Boolean {
	return withContext(Dispatchers.IO) {
		val logDir = File(logDirPath)
		val logFiles = logDir.listFiles { f -> f.extension == "txt" } ?: emptyArray()
		if (logFiles.isEmpty()) return@withContext false

		val shareDir = File(context.cacheDir, "shared_logs")
		shareDir.mkdirs()
		val zipFile = File(shareDir, "hammer-logs.zip")

		ZipOutputStream(zipFile.outputStream()).use { zip ->
			logFiles.forEach { file ->
				zip.putNextEntry(ZipEntry(file.name))
				file.inputStream().use { it.copyTo(zip) }
				zip.closeEntry()
			}
		}

		val uri = FileProvider.getUriForFile(
			context,
			"${context.packageName}.fileprovider",
			zipFile
		)
		val intent = Intent(Intent.ACTION_SEND).apply {
			type = "application/zip"
			putExtra(Intent.EXTRA_STREAM, uri)
			addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
		}

		withContext(Dispatchers.Main) {
			context.startActivity(Intent.createChooser(intent, null))
		}

		true
	}
}
