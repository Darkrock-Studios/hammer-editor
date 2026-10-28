package com.darkrockstudios.apps.hammer.common.projectsync

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.base.diff.DiffResult
import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.common.components.projectsync.ProjectSynchronization
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.WindowSizeClass
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdConflictField
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdConflictFieldSpacing
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdEngravingPlaceholder
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineField
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMonoLabel
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdTagChip
import com.darkrockstudios.apps.hammer.common.compose.images.rememberImageRequest
import com.darkrockstudios.apps.hammer.common.compose.rememberStrRes
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.compose.theme.LocalHammerColors
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.image.ImageFile
import com.darkrockstudios.apps.hammer.sync_conflict_encyclopedia_image_not_local
import com.darkrockstudios.apps.hammer.sync_conflict_encyclopedia_image_not_local_description
import com.darkrockstudios.apps.hammer.sync_conflict_encyclopedia_label_aliases
import com.darkrockstudios.apps.hammer.sync_conflict_encyclopedia_label_content
import com.darkrockstudios.apps.hammer.sync_conflict_encyclopedia_label_image
import com.darkrockstudios.apps.hammer.sync_conflict_encyclopedia_label_name
import com.darkrockstudios.apps.hammer.sync_conflict_encyclopedia_label_tags
import com.darkrockstudios.apps.hammer.sync_conflict_encyclopedia_label_type
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

	val contentDiff = rememberContentDiff(entityConflict.serverEntity.text, contentTextValue)

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
				localImages = entityConflict.localImages,
				nameValue = nameTextValue,
				onNameChange = { nameTextValue = it },
				contentValue = contentTextValue,
				onContentChange = { contentTextValue = it },
				nameError = nameError,
				contentError = contentError,
				contentDiff = contentDiff,
			)
		},
		RemoteBody = { m, c, _ ->
			RemoteEntryBody(
				modifier = m,
				server = c.serverEntity,
				client = c.clientEntity,
				localImages = entityConflict.localImages,
				contentDiff = contentDiff,
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
	localImages: Map<Int, ImageFile>,
	nameValue: String,
	onNameChange: (String) -> Unit,
	contentValue: String,
	onContentChange: (String) -> Unit,
	nameError: String?,
	contentError: String?,
	contentDiff: DiffResult?,
) {
	val contentTransformation = rememberLocalDiffHighlight(contentDiff)
	Column(
		modifier = modifier
			.padding(horizontal = Ui.Padding.XL, vertical = Ui.Padding.L)
			.verticalScroll(rememberScrollState()),
		verticalArrangement = Arrangement.spacedBy(HdConflictFieldSpacing),
	) {
		HeaderRow(
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
		ImagesBlock(
			images = client.images,
			otherSide = server.images,
			localImages = localImages,
			type = client.entryType,
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
				visualTransformation = contentTransformation,
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
	localImages: Map<Int, ImageFile>,
	contentDiff: DiffResult?,
) {
	val highlightedContent = rememberRemoteDiffHighlight(server.text, contentDiff)
	Column(
		modifier = modifier
			.padding(horizontal = Ui.Padding.XL, vertical = Ui.Padding.L)
			.verticalScroll(rememberScrollState()),
		verticalArrangement = Arrangement.spacedBy(HdConflictFieldSpacing),
	) {
		HeaderRow(
			type = server.entryType,
			nameConflict = server.name != client.name,
			editableName = { ReadOnlyLine(server.name) },
		)
		ImagesBlock(
			images = server.images,
			otherSide = client.images,
			localImages = localImages,
			type = server.entryType,
		)
		HdConflictField(
			label = Res.string.sync_conflict_encyclopedia_label_content.get(),
			conflict = server.text != client.text,
		) {
			ReadOnlyBlock(highlightedContent)
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
	type: String,
	nameConflict: Boolean,
	editableName: @Composable () -> Unit,
) {
	Column(
		modifier = Modifier.fillMaxWidth(),
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

/**
 * One side's images in order, as thumbnails. An image this device does not have yet shows as a
 * tile with its id; one the other side does not list is outlined in amber.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ImagesBlock(
	images: List<Int>,
	otherSide: List<Int>,
	localImages: Map<Int, ImageFile>,
	type: String,
) {
	val amber = LocalHammerColors.current.warning
	HdConflictField(
		label = Res.string.sync_conflict_encyclopedia_label_image.get(),
		conflict = images != otherSide,
	) {
		if (images.isEmpty()) {
			HdEngravingPlaceholder(
				label = type.uppercase(),
				modifier = Modifier.size(ImageThumbSize),
			)
		} else {
			FlowRow(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.spacedBy(6.dp),
				verticalArrangement = Arrangement.spacedBy(6.dp),
			) {
				images.forEach { id ->
					val onlyHere = id !in otherSide
					ImageThumb(
						id = id,
						file = localImages[id],
						modifier = Modifier
							.size(ImageThumbSize)
							.border(
								width = if (onlyHere) 1.dp else Dp.Hairline,
								color = if (onlyHere) amber else MaterialTheme.colorScheme.outlineVariant,
								shape = RectangleShape,
							),
					)
				}
			}
		}
	}
}

@Composable
private fun ImageThumb(id: Int, file: ImageFile?, modifier: Modifier) {
	if (file == null) {
		val description = Res.string.sync_conflict_encyclopedia_image_not_local_description.get(id)
		Box(
			modifier = modifier
				.background(MaterialTheme.colorScheme.surfaceContainerHigh)
				.semantics { contentDescription = description },
			contentAlignment = Alignment.Center,
		) {
			HdMonoLabel(
				text = Res.string.sync_conflict_encyclopedia_image_not_local.get(id),
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)
		}
	} else {
		AsyncImage(
			model = rememberImageRequest(path = file.path.path, version = file.contentHash),
			contentDescription = file.def.name,
			contentScale = ContentScale.Crop,
			modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh),
		)
	}
}

private val ImageThumbSize = 64.dp

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
