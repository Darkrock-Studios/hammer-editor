package com.darkrockstudios.apps.hammer.common.preview.projecthome

import com.darkrockstudios.apps.hammer.common.preview.PreviewScrim
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.projecthome.ColorPickerCard
import com.darkrockstudios.apps.hammer.project_info_theme_primary_label

/** The project theme's color picker, editing a teal primary, over a scrim. */
@Composable
internal fun ColorPickerScrimPreview() {
	PreviewScrim {
		ColorPickerCard(
			title = Res.string.project_info_theme_primary_label.get(),
			initial = Color(0xFF2A7F7A),
			onDismiss = {},
			onConfirm = {},
		)
	}
}
