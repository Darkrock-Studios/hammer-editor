package com.darkrockstudios.apps.hammer.common.projectselection.about

import com.darkrockstudios.apps.hammer.about_diagnostics_copy_button
import com.darkrockstudios.apps.hammer.about_diagnostics_row
import com.darkrockstudios.apps.hammer.about_diagnostics_row_desc
import com.darkrockstudios.apps.hammer.about_group_troubleshooting
import com.darkrockstudios.apps.hammer.about_logs_export_short_button
import com.darkrockstudios.apps.hammer.about_logs_row
import com.darkrockstudios.apps.hammer.about_logs_row_share_desc
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMetaText
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdSettingGroup
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdSettingRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.components.projectselection.aboutapp.AboutApp
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineButton
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.getCacheDirectory
import com.darkrockstudios.apps.hammer.common.getPlatformFilesystem
import com.darkrockstudios.apps.hammer.common.platformIoDispatcher
import com.darkrockstudios.apps.hammer.common.util.zip.zipDirectory
import io.github.aakira.napier.Napier
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.Path
import okio.Path.Companion.toPath
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.UIKit.popoverPresentationController
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

@Composable
actual fun PlatformAboutSection(component: AboutApp) {
	val scope = rememberCoroutineScope()
	val state by component.state.subscribeAsState()
	var exporting by remember { mutableStateOf(false) }
	var status by remember { mutableStateOf<String?>(null) }

	HdSettingGroup(title = Res.string.about_group_troubleshooting.get()) {
		HdSettingRow(
			title = Res.string.about_logs_row.get(),
			description = Res.string.about_logs_row_share_desc.get(),
		) {
			HdHairlineButton(
				label = Res.string.about_logs_export_short_button.get(),
				onClick = {
					if (exporting) return@HdHairlineButton
					exporting = true
					scope.launch {
						exportAndShareLogs(state.logDirectoryPath)
						exporting = false
					}
				},
			)
		}
		HdSettingRow(
			title = Res.string.about_diagnostics_row.get(),
			description = Res.string.about_diagnostics_row_desc.get(),
		) {
			CopyDiagnosticsButton(
				logDirectoryPath = state.logDirectoryPath,
				onStatus = { status = it },
				label = Res.string.about_diagnostics_copy_button.get(),
			)
		}
		status?.let { HdMetaText(text = it) }
	}
}

private const val LOGS_ZIP_NAME = "hammer-logs.zip"

private suspend fun exportAndShareLogs(logDirPath: String) {
	val zipPath = withContext(platformIoDispatcher) {
		val fileSystem = getPlatformFilesystem()
		val logsDir = logDirPath.toPath()

		val hasLogs = try {
			fileSystem.exists(logsDir) && fileSystem.list(logsDir).any { it.name.endsWith(".txt") }
		} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
			Napier.e("Failed to list log files", e)
			false
		}

		if (!hasLogs) {
			Napier.i("No log files to share")
			return@withContext null
		}

		val zipPath = getCacheDirectory().toPath() / LOGS_ZIP_NAME
		try {
			if (fileSystem.exists(zipPath)) fileSystem.delete(zipPath)
			zipDirectory(fileSystem, logsDir, zipPath)
			zipPath
		} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
			Napier.e("Failed to zip logs for sharing", e)
			null
		}
	} ?: return

	presentShareSheet(zipPath)
}

@OptIn(ExperimentalForeignApi::class)
private fun presentShareSheet(zipPath: Path) {
	val url = NSURL.fileURLWithPath(zipPath.toString())

	dispatch_async(dispatch_get_main_queue()) {
		val presenter = topViewController()
		if (presenter == null) {
			Napier.w("No view controller available to present log share sheet")
			return@dispatch_async
		}

		val activityVC = UIActivityViewController(
			activityItems = listOf(url),
			applicationActivities = null,
		)

		// iPad requires a popover anchor; without it UIKit raises.
		activityVC.popoverPresentationController?.apply {
			sourceView = presenter.view
			presenter.view.bounds.useContents {
				sourceRect = CGRectMake(size.width / 2.0, size.height / 2.0, 0.0, 0.0)
			}
		}

		presenter.presentViewController(activityVC, animated = true, completion = null)
	}
}

private fun topViewController(): UIViewController? {
	val scene = UIApplication.sharedApplication.connectedScenes
		.firstOrNull { it is UIWindowScene } as? UIWindowScene ?: return null
	val keyWindow = scene.windows.firstOrNull {
		(it as? UIWindow)?.isKeyWindow() == true
	} as? UIWindow ?: return null

	var top: UIViewController? = keyWindow.rootViewController
	while (top?.presentedViewController != null) {
		top = top.presentedViewController
	}
	return top
}
