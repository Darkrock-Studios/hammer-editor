package com.darkrockstudios.apps.hammer.common.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.Padded
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineProgressBar
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdStatus
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdStatusGlyph
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.syncAccLogD
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.syncLogD
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.syncLogE
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.syncLogI
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.syncLogW
import com.darkrockstudios.apps.hammer.common.projectselection.SyncLogMessageUi

@Preview
@Composable
private fun SyncLogRowsPreview() = Padded {
	Column(
		modifier = Modifier.fillMaxWidth().padding(16.dp),
		verticalArrangement = Arrangement.spacedBy(4.dp),
	) {
		SyncLogMessageUi(syncLogI("Sync started", "Apophis"))
		SyncLogMessageUi(syncLogD("Diff scan complete", "Apophis"))
		SyncLogMessageUi(syncLogW("Scene 12 — local newer, kept local", "Insurgency"))
		SyncLogMessageUi(syncLogE("Connection lost (504) — queued for retry", "Alice In Wonderland"))
		SyncLogMessageUi(syncAccLogD("Sync started · 8 projects"))
	}
}

@Preview
@Composable
private fun StatusGlyphPreview() = Padded {
	Column(
		modifier = Modifier.padding(16.dp),
		verticalArrangement = Arrangement.spacedBy(8.dp),
	) {
		HdStatus.entries.forEach { status -> HdStatusGlyph(status) }
	}
}

@Preview
@Composable
private fun HairlineProgressBarPreview() = Padded {
	Column(
		modifier = Modifier.fillMaxWidth().padding(16.dp),
		verticalArrangement = Arrangement.spacedBy(8.dp),
	) {
		HdHairlineProgressBar(progress = 0f)
		HdHairlineProgressBar(progress = 0.33f)
		HdHairlineProgressBar(progress = 0.66f)
		HdHairlineProgressBar(progress = 1f)
	}
}
