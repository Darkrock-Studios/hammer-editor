package com.darkrockstudios.apps.hammer.common.projectsync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.components.projectsync.ProjectSynchronization
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdConflictField
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdConflictFieldSpacing
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineField
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.sync_conflict_timeline_event_missing_date
import com.darkrockstudios.apps.hammer.sync_conflict_title_timeline_event_field_content
import com.darkrockstudios.apps.hammer.sync_conflict_title_timeline_event_field_date

@Composable
internal fun TimelineEventConflict(
	entityConflict: ProjectSynchronization.EntityConflict.TimelineEventConflict,
	component: ProjectSynchronization,
	screenCharacteristics: WindowSizeClass
) {
	val client = entityConflict.clientEntity
	var dateTextValue by rememberSaveable(client) { mutableStateOf(client.date.orEmpty()) }
	var contentTextValue by rememberSaveable(client) { mutableStateOf(client.content) }

	val useLocal = {
		component.resolveConflict(
			client.copy(
				date = dateTextValue.ifBlank { null },
				content = contentTextValue,
			)
		)
		Unit
	}
	val useRemote = { component.resolveConflict(entityConflict.serverEntity); Unit }

	EntityConflict(
		entityConflict = entityConflict,
		component = component,
		screenCharacteristics = screenCharacteristics,
		onUseLocal = useLocal,
		onUseRemote = useRemote,
		LocalBody = { m, c, _ ->
			Column(
				modifier = m
					.padding(horizontal = Ui.Padding.XL, vertical = Ui.Padding.L)
					.verticalScroll(rememberScrollState()),
				verticalArrangement = Arrangement.spacedBy(HdConflictFieldSpacing),
			) {
				HdConflictField(
					label = Res.string.sync_conflict_title_timeline_event_field_date.get(),
					conflict = c.serverEntity.date != c.clientEntity.date,
				) {
					HdHairlineField(
						label = "",
						value = dateTextValue,
						onValueChange = { dateTextValue = it },
						singleLine = true,
						modifier = Modifier.fillMaxWidth(),
					)
				}
				HdConflictField(
					label = Res.string.sync_conflict_title_timeline_event_field_content.get(),
					conflict = c.serverEntity.content != c.clientEntity.content,
				) {
					HdHairlineField(
						label = "",
						value = contentTextValue,
						onValueChange = { contentTextValue = it },
						singleLine = false,
						minLines = 6,
						modifier = Modifier.fillMaxWidth(),
					)
				}
			}
		},
		RemoteBody = { m, c, _ ->
			val missingDateLabel = Res.string.sync_conflict_timeline_event_missing_date.get()
			Column(
				modifier = m
					.padding(horizontal = Ui.Padding.XL, vertical = Ui.Padding.L)
					.verticalScroll(rememberScrollState()),
				verticalArrangement = Arrangement.spacedBy(HdConflictFieldSpacing),
			) {
				HdConflictField(
					label = Res.string.sync_conflict_title_timeline_event_field_date.get(),
					conflict = c.serverEntity.date != c.clientEntity.date,
				) {
					ReadOnlyLine(c.serverEntity.date?.takeIf { it.isNotBlank() } ?: missingDateLabel)
				}
				HdConflictField(
					label = Res.string.sync_conflict_title_timeline_event_field_content.get(),
					conflict = c.serverEntity.content != c.clientEntity.content,
				) {
					ReadOnlyBlock(c.serverEntity.content)
				}
			}
		},
	)
}
