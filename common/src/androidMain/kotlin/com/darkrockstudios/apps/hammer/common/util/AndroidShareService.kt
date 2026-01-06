package com.darkrockstudios.apps.hammer.common.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import io.github.aakira.napier.Napier
import java.io.File

/**
 * Service for sharing files on Android using FileProvider and share intents.
 * Can be used to share any file from the app (backups, exports, documents, etc.)
 */
class AndroidShareService(private val context: Context) {

	/**
	 * Share a file using Android's share sheet.
	 *
	 * @param file The file to share
	 * @param mimeType Optional MIME type (auto-detected if null)
	 * @param chooserTitle Optional title for share sheet (defaults to "Share File")
	 * @return true if share intent was launched successfully
	 */
	fun shareFile(
		file: File,
		mimeType: String? = null,
		chooserTitle: String = "Share File"
	): Boolean {
		return try {
			if (!file.exists()) {
				Napier.w("File does not exist: ${file.path}")
				return false
			}

			val authority = "${context.packageName}.fileprovider"
			val uri = FileProvider.getUriForFile(context, authority, file)

			val resolvedMimeType = mimeType ?: detectMimeType(file)

			val shareIntent = Intent(Intent.ACTION_SEND).apply {
				type = resolvedMimeType
				putExtra(Intent.EXTRA_STREAM, uri)
				addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
				addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
			}

			val chooser = Intent.createChooser(shareIntent, chooserTitle)
			chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

			context.startActivity(chooser)

			Napier.i("Started file share: ${file.name}")
			true
		} catch (e: Exception) {
			Napier.e("Failed to share file: ${file.name}", e)
			false
		}
	}

	/**
	 * Detect MIME type based on file extension.
	 */
	private fun detectMimeType(file: File): String {
		return when (file.extension.lowercase()) {
			"zip" -> "application/zip"
			"pdf" -> "application/pdf"
			"txt" -> "text/plain"
			"json" -> "application/json"
			"xml" -> "application/xml"
			"png" -> "image/png"
			"jpg", "jpeg" -> "image/jpeg"
			else -> "application/octet-stream"
		}
	}
}
