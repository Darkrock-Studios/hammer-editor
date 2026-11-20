package com.darkrockstudios.apps.hammer.common.preview.projecthome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.projecthome.ColorPickerCard
import com.darkrockstudios.apps.hammer.project_info_theme_primary_label

/** The project theme's color picker, editing a teal primary, over a scrim. */
@Composable
internal fun ColorPickerScrimPreview() {
	Box(
		modifier = Modifier
			.fillMaxSize()
			.background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f)),
		contentAlignment = Alignment.Center,
	) {
		ColorPickerCard(
			title = Res.string.project_info_theme_primary_label.get(),
			initial = Color(0xFF2A7F7A),
			onDismiss = {},
			onConfirm = {},
		)
	}
}
