package com.darkrockstudios.apps.hammer.common.preview.projectselection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.darkrockstudios.apps.hammer.common.components.projectselection.ProjectSelection
import com.darkrockstudios.apps.hammer.common.projectselection.ChangelogContent

private val previewNotes = """
New features
- Reader Mode: read your story as prose, one chapter at a time, instead of through the scene editor. Open it from the Read button under the scene list, the book icon in the scene editor, or the editor menu. The scene list becomes a table of contents, and the reader has its own font size
- Export: three new options in the Export Story dialog. Number chapter titles, pick a font (Word, RTF and EPUB), and choose whether to keep blank lines between paragraphs
- Android and iOS: an OLED theme, the dark theme on a pure black background
- Text editor 3.0: Huge improvements in input and performance
- Turkish translation

Improvements
- Syncing with unsaved work now asks Save, Discard or Cancel first, where before it silently saved every unsaved scene
- The Backup Manager lists backups of projects that are no longer on disk, so a deleted project can be restored
- See https://github.com/Wavesonics/hammer-editor/releases for older notes
""".trim()

/** The What's New dialog over a scrim, as it shows over the project list. */
@Composable
fun ChangelogDialogPreview() {
	Box(
		modifier = Modifier
			.fillMaxSize()
			.background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f)),
		contentAlignment = Alignment.Center,
	) {
		ChangelogContent(
			state = ProjectSelection.ChangelogState(
				visible = true,
				version = "3.12.0",
				date = "2026-10-5",
				notes = previewNotes,
			),
			onDismiss = {},
			onOpenRelease = {},
		)
	}
}
