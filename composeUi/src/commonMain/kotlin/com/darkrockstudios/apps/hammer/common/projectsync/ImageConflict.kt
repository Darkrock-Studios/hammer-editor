package com.darkrockstudios.apps.hammer.common.projectsync

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.synchronizer.EntityHasher
import com.darkrockstudios.apps.hammer.base.image.ImageFormat
import com.darkrockstudios.apps.hammer.common.components.projectsync.ProjectSynchronization
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.WindowSizeClass
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdConflictField
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdConflictFieldSpacing
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineField
import com.darkrockstudios.apps.hammer.common.compose.formatByteSize
import com.darkrockstudios.apps.hammer.common.compose.images.rememberImageRequest
import com.darkrockstudios.apps.hammer.common.compose.rememberStrRes
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.images.ImageViewerDialog
import com.darkrockstudios.apps.hammer.common.images.ImageViewerItem
import com.darkrockstudios.apps.hammer.sync_conflict_entity_label_image
import com.darkrockstudios.apps.hammer.sync_conflict_image_description
import com.darkrockstudios.apps.hammer.sync_conflict_image_unreadable
import com.darkrockstudios.apps.hammer.sync_conflict_title_image_field_name
import kotlinx.coroutines.launch

@Composable
internal fun ImageConflict(
	entityConflict: ProjectSynchronization.EntityConflict.ImageConflict,
	component: ProjectSynchronization,
	screenCharacteristics: WindowSizeClass
) {
	val scope = rememberCoroutineScope()
	val strRes = rememberStrRes()
	val client = entityConflict.clientEntity
	var nameTextValue by rememberSaveable(client) { mutableStateOf(client.name) }
	var nameError by rememberSaveable(client) { mutableStateOf<String?>(null) }

	val clientImage = remember(client) { ConflictImage.of(client) }
	val serverImage = remember(entityConflict.serverEntity) { ConflictImage.of(entityConflict.serverEntity) }
	// Bytes and extension only: a rename alone is shown on the name field.
	val imagesDiffer = remember(clientImage, serverImage) {
		clientImage.fileExtension != serverImage.fileExtension || !(clientImage.bytes contentEquals serverImage.bytes)
	}

	val useLocal = {
		val error = component.resolveConflict(client.copy(name = nameTextValue.trim()))
		if (error is ProjectSynchronization.EntityMergeError.ImageMergeError) {
			scope.launch { nameError = error.nameError?.text(strRes) }
		}
	}
	val useRemote = { component.resolveConflict(entityConflict.serverEntity); Unit }

	var viewing by remember { mutableStateOf<ImageViewerItem?>(null) }
	viewing?.let { item ->
		ImageViewerDialog(items = listOf(item), initialIndex = 0, onDismissed = { viewing = null })
	}

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
					label = Res.string.sync_conflict_title_image_field_name.get(),
					conflict = c.serverEntity.name != c.clientEntity.name,
				) {
					HdHairlineField(
						label = "",
						value = nameTextValue,
						onValueChange = { nameTextValue = it },
						singleLine = true,
						error = nameError,
						modifier = Modifier.fillMaxWidth(),
					)
				}
				HdConflictField(
					label = Res.string.sync_conflict_entity_label_image.get(),
					conflict = imagesDiffer,
				) {
					ConflictImagePreview(
						request = rememberImageRequest(
							path = entityConflict.clientImagePath.path,
							version = clientImage.digest,
						),
						image = clientImage,
						onOpen = {
							viewing = ImageViewerItem.ofFile(
								path = entityConflict.clientImagePath.path,
								version = clientImage.digest,
								label = c.clientEntity.name,
							)
						},
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
					label = Res.string.sync_conflict_title_image_field_name.get(),
					conflict = c.serverEntity.name != c.clientEntity.name,
				) {
					ReadOnlyLine(c.serverEntity.name)
				}
				HdConflictField(
					label = Res.string.sync_conflict_entity_label_image.get(),
					conflict = imagesDiffer,
				) {
					val context = LocalPlatformContext.current
					// Bytes have no default memory cache key, so this names them.
					val cacheKey = "sync-conflict-remote-${c.serverEntity.id}-${serverImage.digest}"
					ConflictImagePreview(
						request = remember(context, serverImage, c.serverEntity.id) {
							ImageRequest.Builder(context)
								.data(serverImage.bytes)
								.memoryCacheKey(cacheKey)
								.build()
						},
						image = serverImage,
						onOpen = {
							serverImage.bytes?.let { bytes ->
								viewing = ImageViewerItem.ofBytes(
									bytes = bytes,
									cacheKey = cacheKey,
									label = c.serverEntity.name,
								)
							}
						},
					)
				}
			}
		},
	)
}

/**
 * One side's decoded image. [bytes] and [digest] are null when the base64 does not decode; [digest]
 * only keys the preview cache.
 */
private class ConflictImage(
	val bytes: ByteArray?,
	val digest: String?,
	val fileExtension: String,
) {
	companion object {
		fun of(entity: ApiProjectEntity.ImageEntity): ConflictImage {
			val bytes = ImageFormat.decodeBase64(entity.base64)
			val digest = bytes?.let { EntityHasher.hashImage(entity.id, entity.name, entity.fileExtension, it) }
			return ConflictImage(bytes, digest, entity.fileExtension)
		}
	}
}

@Composable
private fun ConflictImagePreview(request: ImageRequest, image: ConflictImage, onOpen: () -> Unit) {
	if (image.bytes == null) {
		Text(
			text = Res.string.sync_conflict_image_unreadable.get(),
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.error,
		)
		return
	}

	Column(verticalArrangement = Arrangement.spacedBy(Ui.Padding.M)) {
		AsyncImage(
			model = request,
			contentDescription = null,
			contentScale = ContentScale.Fit,
			modifier = Modifier
				.fillMaxWidth()
				.heightIn(max = 320.dp)
				.clickable(onClick = onOpen),
		)
		Text(
			text = Res.string.sync_conflict_image_description.get(
				image.fileExtension.uppercase(),
				formatByteSize(image.bytes.size.toLong()),
			),
			style = MaterialTheme.typography.bodySmall,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
	}
}
