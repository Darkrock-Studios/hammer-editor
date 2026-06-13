package com.darkrockstudios.apps.hammer.common.data.projectbackup

import io.github.aakira.napier.Napier
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
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

actual class BackupManagerService {
	@OptIn(ExperimentalForeignApi::class)
	actual fun exportBackup(backup: ProjectBackupDef) {
		val fileUrl = NSURL.fileURLWithPath(backup.path.path)

		dispatch_async(dispatch_get_main_queue()) {
			val presenter = topViewController()
			if (presenter == null) {
				Napier.w("No view controller available to present backup share sheet")
				return@dispatch_async
			}

			val activityVC = UIActivityViewController(
				activityItems = listOf(fileUrl),
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
}
