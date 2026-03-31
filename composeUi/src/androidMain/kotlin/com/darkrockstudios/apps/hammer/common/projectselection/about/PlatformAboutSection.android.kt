package com.darkrockstudios.apps.hammer.common.projectselection.about

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.about_logs_export_button
import com.darkrockstudios.apps.hammer.about_logs_header
import com.darkrockstudios.apps.hammer.about_logs_no_logs
import com.darkrockstudios.apps.hammer.common.components.projectselection.aboutapp.AboutApp
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@Composable
actual fun PlatformAboutSection(component: AboutApp) {
	val context = LocalContext.current
	val scope = rememberCoroutineScope()
	val state by component.state.subscribeAsState()
	var exporting by remember { mutableStateOf(false) }
	val noLogsMessage = Res.string.about_logs_no_logs.get()

	Spacer(modifier = Modifier.size(Ui.Padding.XL))

	Column {
		Text(
			text = Res.string.about_logs_header.get(),
			style = MaterialTheme.typography.headlineSmall,
		)

		Spacer(modifier = Modifier.size(Ui.Padding.S))

		Button(
			onClick = {
				exporting = true
				scope.launch {
					val success = exportAndShareLogs(context, state.logDirectoryPath)
					if (!success) {
						Toast.makeText(context, noLogsMessage, Toast.LENGTH_SHORT).show()
					}
					exporting = false
				}
			},
			enabled = !exporting
		) {
			Text(Res.string.about_logs_export_button.get())
		}
	}
}

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
