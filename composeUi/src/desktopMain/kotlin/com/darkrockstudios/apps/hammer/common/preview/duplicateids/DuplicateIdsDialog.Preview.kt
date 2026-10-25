package com.darkrockstudios.apps.hammer.common.preview.duplicateids

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.darkrockstudios.apps.hammer.common.components.duplicateids.DuplicateIds
import com.darkrockstudios.apps.hammer.common.compose.theme.AppTheme
import com.darkrockstudios.apps.hammer.common.data.duplicateids.DuplicateCopy
import com.darkrockstudios.apps.hammer.common.data.duplicateids.DuplicateIdKind
import com.darkrockstudios.apps.hammer.common.data.duplicateids.DuplicateIdSet
import com.darkrockstudios.apps.hammer.common.duplicateids.DuplicateIdsContent
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.preview.KoinApplicationPreview
import com.darkrockstudios.apps.hammer.common.preview.globalSettingsPreview

private fun copy(relativePath: String, name: String, text: String, hash: String) = DuplicateCopy(
	path = HPath("/projects/Saga/$relativePath", relativePath.substringAfterLast('/'), true),
	relativePath = relativePath,
	name = name,
	isGroup = false,
	archived = false,
	sizeBytes = text.length.toLong() + 120,
	modifiedAtMillis = 1_793_000_000_000,
	text = text,
	contentHash = hash,
)

private val entrySet = DuplicateIdSet(
	kind = DuplicateIdKind.Entry,
	id = 5,
	copies = listOf(
		copy("encyclopedia/person/person~5~Bob.toml", "Bob", "A blacksmith in the lower town, quick to anger and slow to forgive.", "a"),
		copy(
			"encyclopedia/person/person~5~Bob.sync-conflict-20261024-173944-53PQXWR.toml",
			"Bob",
			"A blacksmith in the lower town, quick to anger and slower still to forgive his brother.",
			"b",
		),
		copy("encyclopedia/person/person~5~Bobby.toml", "Bobby", "Bob's son, apprenticed at the forge.", "c"),
	),
)

private val previewState = DuplicateIds.State(
	sets = listOf(entrySet, entrySet.copy(id = 12)),
	keep = setOf(entrySet.copies[0].path, entrySet.copies[2].path),
)

@Composable
private fun DuplicateIdsPreviewScaffold(state: DuplicateIds.State, darkTheme: Boolean) {
	KoinApplicationPreview {
		AppTheme(globalSettingsPreview, useDarkTheme = darkTheme) {
			Box(
				modifier = Modifier
					.fillMaxSize()
					.background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f)),
				contentAlignment = Alignment.Center,
			) {
				DuplicateIdsContent(state = state, onToggleKeep = {}, onKeep = {}, onDecideLater = {})
			}
		}
	}
}

@Preview(widthDp = 720, heightDp = 760)
@Composable
fun DuplicateIdsDialogPreview() {
	DuplicateIdsPreviewScaffold(previewState, darkTheme = false)
}

@Preview(widthDp = 720, heightDp = 760)
@Composable
fun DuplicateIdsDialogDarkPreview() {
	DuplicateIdsPreviewScaffold(previewState, darkTheme = true)
}

@Preview(widthDp = 380, heightDp = 760)
@Composable
fun DuplicateIdsDialogPhonePreview() {
	DuplicateIdsPreviewScaffold(previewState, darkTheme = false)
}
