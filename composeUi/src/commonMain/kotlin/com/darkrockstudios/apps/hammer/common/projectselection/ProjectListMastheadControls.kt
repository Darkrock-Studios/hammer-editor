package com.darkrockstudios.apps.hammer.common.projectselection

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.runtime.Composable
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdQuietButton
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.projects_list_sync_status_idle
import com.darkrockstudios.apps.hammer.projects_list_sync_status_syncing

/** Sync state and the sync action in one control: its presence says sync is set up. */
@Composable
internal fun SyncStatusButton(syncing: Boolean, onClick: () -> Unit) {
	HdQuietButton(
		onClick = onClick,
		icon = if (syncing) Icons.Outlined.CloudSync else Icons.Outlined.Cloud,
		label = if (syncing) {
			Res.string.projects_list_sync_status_syncing.get()
		} else {
			Res.string.projects_list_sync_status_idle.get()
		},
		active = syncing,
	)
}
