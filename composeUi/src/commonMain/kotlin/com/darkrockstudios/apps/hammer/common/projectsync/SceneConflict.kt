package com.darkrockstudios.apps.hammer.common.projectsync

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.base.diff.DiffSpan
import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.common.components.projectsync.ProjectSynchronization
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.designsystem.*
import com.darkrockstudios.apps.hammer.common.compose.rememberStrRes
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.encyclopedia.EntryRefChipLabel
import com.darkrockstudios.apps.hammer.common.encyclopedia.UnknownEntryRefChipLabel
import kotlinx.coroutines.launch

private enum class SceneConflictTab { CONTENT, METADATA, REFERENCES }

@Composable
internal fun SceneConflict(
	entityConflict: ProjectSynchronization.EntityConflict.SceneConflict,
	component: ProjectSynchronization,
	screenCharacteristics: WindowSizeClass
) {
	val scope = rememberCoroutineScope()
	val strRes = rememberStrRes()
	val client = entityConflict.clientEntity
	var selectedTab by rememberSaveable { mutableStateOf(SceneConflictTab.CONTENT) }

	var nameTextValue by rememberSaveable(client) { mutableStateOf(client.name) }
	var contentTextValue by rememberSaveable(client) { mutableStateOf(client.content) }
	var outlineTextValue by rememberSaveable(client) { mutableStateOf(client.outline) }
	var notesTextValue by rememberSaveable(client) { mutableStateOf(client.notes) }
	var nameError by rememberSaveable(client) { mutableStateOf<String?>(null) }

	// Word-level diff between the server content and the (editable) local content
	val server = entityConflict.serverEntity
	val contentDiff = rememberContentDiff(server.content, contentTextValue)
	val deletedStyle = diffDeletedStyle()
	val insertedStyle = diffInsertedStyle()

	val useLocal = {
		val error = component.resolveConflict(
			client.copy(
				name = nameTextValue,
				content = contentTextValue,
				outline = outlineTextValue,
				notes = notesTextValue,
			)
		)
		if (error is ProjectSynchronization.EntityMergeError.SceneMergeError) {
			scope.launch { nameError = error.nameError?.text(strRes) }
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
			LocalSceneBody(
				modifier = m,
				server = c.serverEntity,
				client = c.clientEntity,
				tab = selectedTab,
				nameValue = nameTextValue,
				onNameChange = { nameTextValue = it },
				contentValue = contentTextValue,
				onContentChange = { contentTextValue = it },
				outlineValue = outlineTextValue,
				onOutlineChange = { outlineTextValue = it },
				notesValue = notesTextValue,
				onNotesChange = { notesTextValue = it },
				nameError = nameError,
				contentInsertedSpans = contentDiff?.rightSpans.orEmpty(),
				insertedStyle = insertedStyle,
				component = component,
			)
		},
		RemoteBody = { m, c, _ ->
			RemoteSceneBody(
				modifier = m,
				server = c.serverEntity,
				client = c.clientEntity,
				tab = selectedTab,
				contentDeletedSpans = contentDiff?.leftSpans.orEmpty(),
				deletedStyle = deletedStyle,
				component = component,
			)
		},
		bottomBar = {
			SceneTabPicker(selected = selectedTab, onSelect = { selectedTab = it })
		},
	)
}

@Composable
private fun SceneTabPicker(
	selected: SceneConflictTab,
	onSelect: (SceneConflictTab) -> Unit,
) {
	val contentLabel = Res.string.sync_conflict_scene_tab_content.get()
	val metaLabel = Res.string.sync_conflict_scene_tab_metadata.get()
	val refsLabel = Res.string.sync_conflict_scene_tab_references.get()
	Box(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow)) {
		HdHairlineSegmentedPicker(
			modifier = Modifier.fillMaxWidth().padding(horizontal = Ui.Padding.XL, vertical = Ui.Padding.M),
			options = SceneConflictTab.entries,
			selected = selected,
			onSelect = onSelect,
			label = {
				when (it) {
					SceneConflictTab.CONTENT -> contentLabel
					SceneConflictTab.METADATA -> metaLabel
					SceneConflictTab.REFERENCES -> refsLabel
				}
			},
		)
	}
}

@Composable
private fun LocalSceneBody(
	modifier: Modifier,
	server: ApiProjectEntity.SceneEntity,
	client: ApiProjectEntity.SceneEntity,
	tab: SceneConflictTab,
	nameValue: String,
	onNameChange: (String) -> Unit,
	contentValue: String,
	onContentChange: (String) -> Unit,
	outlineValue: String,
	onOutlineChange: (String) -> Unit,
	notesValue: String,
	onNotesChange: (String) -> Unit,
	nameError: String?,
	contentInsertedSpans: List<DiffSpan>,
	insertedStyle: SpanStyle,
	component: ProjectSynchronization,
) {
	Column(
		modifier = modifier
			.padding(horizontal = Ui.Padding.XL, vertical = Ui.Padding.L)
			.verticalScroll(rememberScrollState()),
		verticalArrangement = Arrangement.spacedBy(HdConflictFieldSpacing),
	) {
		when (tab) {
			SceneConflictTab.CONTENT -> {
				val contentTransformation = remember(contentInsertedSpans, insertedStyle) {
					DiffHighlightTransformation(contentInsertedSpans, insertedStyle)
				}
				HdConflictField(
					label = Res.string.sync_conflict_title_scene_field_content.get(),
					conflict = server.content != client.content,
				) {
					HdHairlineField(
						label = "",
						value = contentValue,
						onValueChange = onContentChange,
						singleLine = false,
						minLines = 8,
						visualTransformation = contentTransformation,
						modifier = Modifier.fillMaxWidth(),
					)
				}
			}
			SceneConflictTab.METADATA -> {
				HdConflictField(
					label = Res.string.sync_conflict_title_scene_field_name.get(),
					conflict = server.name != client.name,
				) {
					HdHairlineField(
						label = "",
						value = nameValue,
						onValueChange = onNameChange,
						singleLine = true,
						error = nameError,
						modifier = Modifier.fillMaxWidth(),
					)
				}
				HdConflictField(
					label = Res.string.sync_conflict_title_scene_field_outline.get(),
					conflict = server.outline != client.outline,
				) {
					HdHairlineField(
						label = "",
						value = outlineValue,
						onValueChange = onOutlineChange,
						singleLine = false,
						minLines = 3,
						modifier = Modifier.fillMaxWidth(),
					)
				}
				HdConflictField(
					label = Res.string.sync_conflict_title_scene_field_notes.get(),
					conflict = server.notes != client.notes,
				) {
					HdHairlineField(
						label = "",
						value = notesValue,
						onValueChange = onNotesChange,
						singleLine = false,
						minLines = 3,
						modifier = Modifier.fillMaxWidth(),
					)
				}
			}
			SceneConflictTab.REFERENCES -> {
				ReferencesTabBody(
					confirmedIds = client.confirmedReferences,
					dismissedIds = client.dismissedReferences,
					confirmedConflict = server.confirmedReferences != client.confirmedReferences,
					dismissedConflict = server.dismissedReferences != client.dismissedReferences,
					component = component,
				)
			}
		}
	}
}

@Composable
private fun RemoteSceneBody(
	modifier: Modifier,
	server: ApiProjectEntity.SceneEntity,
	client: ApiProjectEntity.SceneEntity,
	tab: SceneConflictTab,
	contentDeletedSpans: List<DiffSpan>,
	deletedStyle: SpanStyle,
	component: ProjectSynchronization,
) {
	Column(
		modifier = modifier
			.padding(horizontal = Ui.Padding.XL, vertical = Ui.Padding.L)
			.verticalScroll(rememberScrollState()),
		verticalArrangement = Arrangement.spacedBy(HdConflictFieldSpacing),
	) {
		when (tab) {
			SceneConflictTab.CONTENT -> {
				val highlighted = remember(server.content, contentDeletedSpans, deletedStyle) {
					diffHighlightedString(server.content, contentDeletedSpans, deletedStyle)
				}
				HdConflictField(
					label = Res.string.sync_conflict_title_scene_field_content.get(),
					conflict = server.content != client.content,
				) {
					ReadOnlyBlock(highlighted)
				}
			}
			SceneConflictTab.METADATA -> {
				HdConflictField(
					label = Res.string.sync_conflict_title_scene_field_name.get(),
					conflict = server.name != client.name,
				) { ReadOnlyLine(server.name) }
				HdConflictField(
					label = Res.string.sync_conflict_title_scene_field_outline.get(),
					conflict = server.outline != client.outline,
				) { ReadOnlyBlock(server.outline) }
				HdConflictField(
					label = Res.string.sync_conflict_title_scene_field_notes.get(),
					conflict = server.notes != client.notes,
				) { ReadOnlyBlock(server.notes) }
			}
			SceneConflictTab.REFERENCES -> {
				ReferencesTabBody(
					confirmedIds = server.confirmedReferences,
					dismissedIds = server.dismissedReferences,
					confirmedConflict = server.confirmedReferences != client.confirmedReferences,
					dismissedConflict = server.dismissedReferences != client.dismissedReferences,
					component = component,
				)
			}
		}
	}
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReferencesTabBody(
	confirmedIds: Set<Int>,
	dismissedIds: Set<Int>,
	confirmedConflict: Boolean,
	dismissedConflict: Boolean,
	component: ProjectSynchronization,
) {
	HdConflictField(
		label = Res.string.sync_conflict_references_confirmed_label.get(),
		conflict = confirmedConflict,
	) {
		ReferenceChipFlow(ids = confirmedIds, component = component)
	}
	Spacer(modifier = Modifier.size(Ui.Padding.L))
	HdConflictField(
		label = Res.string.sync_conflict_references_dismissed_label.get(),
		conflict = dismissedConflict,
	) {
		ReferenceChipFlow(ids = dismissedIds, component = component)
	}
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReferenceChipFlow(
	ids: Set<Int>,
	component: ProjectSynchronization,
) {
	if (ids.isEmpty()) {
		HdMonoLabel(
			text = Res.string.sync_conflict_references_empty.get(),
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
		return
	}
	val resolved = remember(ids, component) {
		ids.map { it to component.resolveEntryRef(it) }
	}
	FlowRow(
		modifier = Modifier.fillMaxWidth(),
		horizontalArrangement = Arrangement.spacedBy(8.dp),
		verticalArrangement = Arrangement.spacedBy(8.dp),
	) {
		for ((id, def) in resolved) {
			Box(
				modifier = Modifier
					.border(
						width = Dp.Hairline,
						color = MaterialTheme.colorScheme.outlineVariant,
						shape = RectangleShape,
					)
					.padding(horizontal = 8.dp, vertical = 4.dp),
			) {
				if (def != null) {
					EntryRefChipLabel(type = def.type, name = def.name)
				} else {
					UnknownEntryRefChipLabel(id = id)
				}
			}
		}
	}
}
