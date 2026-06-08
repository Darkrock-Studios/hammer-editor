package com.darkrockstudios.apps.hammer.common.projectselection.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.about_logs_directory_label
import com.darkrockstudios.apps.hammer.about_logs_header
import com.darkrockstudios.apps.hammer.about_logs_open_tooltip
import com.darkrockstudios.apps.hammer.common.components.projectselection.aboutapp.AboutApp
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineButton
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineSection
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMetadataItem
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.getLogDirectory
import io.github.aakira.napier.Napier
import java.awt.Desktop
import java.io.File

@Composable
actual fun PlatformAboutSection(component: AboutApp, section: Int) {
	val logDir = getLogDirectory() ?: return

	HdHairlineSection(
		section = section,
		title = Res.string.about_logs_header.get(),
	) {
		Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
			HdMetadataItem(
				label = Res.string.about_logs_directory_label.get(),
				value = logDir,
				selectable = true,
			)
			HdHairlineButton(
				label = Res.string.about_logs_open_tooltip.get(),
				onClick = { openLogDirectory(logDir) },
			)
		}
	}
}

actual val platformAboutSectionCount: Int = 1

private fun openLogDirectory(logDir: String) {
	try {
		val dir = File(logDir)
		if (dir.exists() && Desktop.isDesktopSupported()) {
			Desktop.getDesktop().open(dir)
		}
	} catch (@Suppress("TooGenericExceptionCaught") e: Exception) { // Desktop.open can throw varied IO/security errors
		Napier.e("Failed to open log directory", e)
	}
}
