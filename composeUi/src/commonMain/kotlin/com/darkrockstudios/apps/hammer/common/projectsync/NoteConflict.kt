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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.components.projectsync.ProjectSynchronization
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdConflictField
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdConflictFieldSpacing
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineField
import com.darkrockstudios.apps.hammer.common.compose.rememberStrRes
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.sync_conflict_title_note_field_name
import kotlinx.coroutines.launch

@Composable
internal fun NoteConflict(
	entityConflict: ProjectSynchronization.EntityConflict.NoteConflict,
	component: ProjectSynchronization,
	screenCharacteristics: WindowSizeClass
) {
	val scope = rememberCoroutineScope()
	val strRes = rememberStrRes()
	val client = entityConflict.clientEntity
	var contentTextValue by rememberSaveable(client) { mutableStateOf(client.content) }
	var contentError by rememberSaveable(client) { mutableStateOf<String?>(null) }

	val useLocal = {
		val error = component.resolveConflict(client.copy(content = contentTextValue))
		if (error is ProjectSynchronization.EntityMergeError.NoteMergeError) {
			scope.launch { contentError = error.noteError?.text(strRes) }
		}
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
					label = Res.string.sync_conflict_title_note_field_name.get(),
					conflict = c.serverEntity.content != c.clientEntity.content,
				) {
					HdHairlineField(
						label = "",
						value = contentTextValue,
						onValueChange = { contentTextValue = it },
						singleLine = false,
						minLines = 6,
						error = contentError,
						modifier = Modifier.fillMaxWidth(),
					)
				}
			}
		},
		RemoteBody = { m, c, _ ->
			Column(
				modifier = m
					.padding(horizontal = Ui.Padding.XL, vertical = Ui.Padding.L)
					.verticalScroll(rememberScrollState()),
				verticalArrangement = Arrangement.spacedBy(HdConflictFieldSpacing),
			) {
				HdConflictField(
					label = Res.string.sync_conflict_title_note_field_name.get(),
					conflict = c.serverEntity.content != c.clientEntity.content,
				) {
					ReadOnlyBlock(c.serverEntity.content)
				}
			}
		},
	)
}
