package com.darkrockstudios.apps.hammer.common.projectsync

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.common.components.projectsync.ProjectSynchronization
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdConflictField
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdConflictFieldSpacing
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdEngravingPlaceholder
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineField
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMonoLabel
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdTagChip
import com.darkrockstudios.apps.hammer.common.compose.rememberStrRes
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.compose.theme.LocalHammerColors
import com.darkrockstudios.apps.hammer.sync_conflict_encyclopedia_label_aliases
import com.darkrockstudios.apps.hammer.sync_conflict_encyclopedia_label_content
import com.darkrockstudios.apps.hammer.sync_conflict_encyclopedia_label_image
import com.darkrockstudios.apps.hammer.sync_conflict_encyclopedia_label_name
import com.darkrockstudios.apps.hammer.sync_conflict_encyclopedia_label_tags
import com.darkrockstudios.apps.hammer.sync_conflict_encyclopedia_label_type
import com.darkrockstudios.apps.hammer.sync_conflict_encyclopedia_has_image
import kotlinx.coroutines.launch

@Composable
internal fun EncyclopediaEntryConflict(
	entityConflict: ProjectSynchronization.EntityConflict.EncyclopediaEntryConflict,
	component: ProjectSynchronization,
	screenCharacteristics: WindowSizeClass
) {
	val scope = rememberCoroutineScope()
	val strRes = rememberStrRes()
	val client = entityConflict.clientEntity
	var nameTextValue by rememberSaveable(client) { mutableStateOf(client.name) }
	var contentTextValue by rememberSaveable(client) { mutableStateOf(client.text) }
	var nameError by rememberSaveable(client) { mutableStateOf<String?>(null) }
	var contentError by rememberSaveable(client) { mutableStateOf<String?>(null) }

	val useLocal = {
		val error = component.resolveConflict(
			client.copy(name = nameTextValue, text = contentTextValue)
		)
		if (error is ProjectSynchronization.EntityMergeError.EncyclopediaEntryMergeError) {
			scope.launch {
				nameError = error.nameError?.text(strRes)
				contentError = error.contentError?.text(strRes)
			}
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
			LocalEntryBody(
				modifier = m,
				server = c.serverEntity,
				client = c.clientEntity,
				nameValue = nameTextValue,
				onNameChange = { nameTextValue = it },
				contentValue = contentTextValue,
				onContentChange = { contentTextValue = it },
				nameError = nameError,
				contentError = contentError,
			)
		},
		RemoteBody = { m, c, _ ->
			RemoteEntryBody(
				modifier = m,
				server = c.serverEntity,
				client = c.clientEntity,
			)
		},
	)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LocalEntryBody(
	modifier: Modifier,
	server: ApiProjectEntity.EncyclopediaEntryEntity,
	client: ApiProjectEntity.EncyclopediaEntryEntity,
	nameValue: String,
	onNameChange: (String) -> Unit,
	contentValue: String,
	onContentChange: (String) -> Unit,
	nameError: String?,
	contentError: String?,
) {
	Column(
		modifier = modifier
			.padding(horizontal = Ui.Padding.XL, vertical = Ui.Padding.L)
			.verticalScroll(rememberScrollState()),
		verticalArrangement = Arrangement.spacedBy(HdConflictFieldSpacing),
	) {
		HeaderRow(
			imageConflict = (server.image != null) != (client.image != null),
			hasImage = client.image != null,
			name = nameValue,
			type = client.entryType,
			nameConflict = server.name != client.name,
			editableName = {
				HdHairlineField(
					label = "",
					value = nameValue,
					onValueChange = onNameChange,
					singleLine = true,
					error = nameError,
					modifier = Modifier.fillMaxWidth(),
				)
			},
		)
		HdConflictField(
			label = Res.string.sync_conflict_encyclopedia_label_content.get(),
			conflict = server.text != client.text,
		) {
			HdHairlineField(
				label = "",
				value = contentValue,
				onValueChange = onContentChange,
				singleLine = false,
				minLines = 6,
				error = contentError,
				modifier = Modifier.fillMaxWidth(),
			)
		}
		ChipBlock(
			label = Res.string.sync_conflict_encyclopedia_label_tags.get(),
			items = client.tags,
			otherSide = server.tags,
			conflict = server.tags != client.tags,
		)
		ChipBlock(
			label = Res.string.sync_conflict_encyclopedia_label_aliases.get(),
			items = client.aliases,
			otherSide = server.aliases,
			conflict = server.aliases != client.aliases,
		)
	}
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RemoteEntryBody(
	modifier: Modifier,
	server: ApiProjectEntity.EncyclopediaEntryEntity,
	client: ApiProjectEntity.EncyclopediaEntryEntity,
) {
	Column(
		modifier = modifier
			.padding(horizontal = Ui.Padding.XL, vertical = Ui.Padding.L)
			.verticalScroll(rememberScrollState()),
		verticalArrangement = Arrangement.spacedBy(HdConflictFieldSpacing),
	) {
		HeaderRow(
			imageConflict = (server.image != null) != (client.image != null),
			hasImage = server.image != null,
			name = server.name,
			type = server.entryType,
			nameConflict = server.name != client.name,
			editableName = { ReadOnlyLine(server.name) },
		)
		HdConflictField(
			label = Res.string.sync_conflict_encyclopedia_label_content.get(),
			conflict = server.text != client.text,
		) {
			ReadOnlyBlock(server.text)
		}
		ChipBlock(
			label = Res.string.sync_conflict_encyclopedia_label_tags.get(),
			items = server.tags,
			otherSide = client.tags,
			conflict = server.tags != client.tags,
		)
		ChipBlock(
			label = Res.string.sync_conflict_encyclopedia_label_aliases.get(),
			items = server.aliases,
			otherSide = client.aliases,
			conflict = server.aliases != client.aliases,
		)
	}
}

@Composable
private fun HeaderRow(
	imageConflict: Boolean,
	hasImage: Boolean,
	name: String,
	type: String,
	nameConflict: Boolean,
	editableName: @Composable () -> Unit,
) {
	Row(
		modifier = Modifier.fillMaxWidth(),
		horizontalArrangement = Arrangement.spacedBy(Ui.Padding.L),
	) {
		HdConflictField(
			label = Res.string.sync_conflict_encyclopedia_label_image.get(),
			conflict = imageConflict,
			modifier = Modifier.size(width = 112.dp, height = 124.dp),
		) {
			if (hasImage) {
				Box(
					modifier = Modifier
						.size(96.dp)
						.background(MaterialTheme.colorScheme.surfaceContainerHigh),
					contentAlignment = androidx.compose.ui.Alignment.Center,
				) {
					HdMonoLabel(
						text = Res.string.sync_conflict_encyclopedia_has_image.get(),
						color = MaterialTheme.colorScheme.onSurfaceVariant,
					)
				}
			} else {
				HdEngravingPlaceholder(
					label = type.uppercase(),
					modifier = Modifier.size(96.dp),
				)
			}
		}
		Column(
			modifier = Modifier.weight(1f),
			verticalArrangement = Arrangement.spacedBy(Ui.Padding.M),
		) {
			HdConflictField(
				label = Res.string.sync_conflict_encyclopedia_label_name.get(),
				conflict = nameConflict,
			) { editableName() }
			HdConflictField(
				label = Res.string.sync_conflict_encyclopedia_label_type.get(),
				conflict = false,
			) {
				Text(
					text = type,
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.onSurface,
				)
			}
		}
	}
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipBlock(
	label: String,
	items: Collection<String>,
	otherSide: Collection<String>,
	conflict: Boolean,
) {
	val amber = LocalHammerColors.current.warning
	HdConflictField(label = label, conflict = conflict) {
		if (items.isEmpty()) {
			HdMonoLabel(
				text = "—",
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)
		} else {
			FlowRow(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.spacedBy(6.dp),
				verticalArrangement = Arrangement.spacedBy(6.dp),
			) {
				items.forEach { tag ->
					val onlyHere = tag !in otherSide
					HdTagChip(
						label = tag,
						active = onlyHere,
						accent = if (onlyHere) amber else null,
					)
				}
			}
		}
	}
}
