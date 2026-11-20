package com.darkrockstudios.apps.hammer.common.preview.images

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.compose.ConfirmationActions
import com.darkrockstudios.apps.hammer.common.compose.IndexStripCard
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.references.ImageReferrer
import com.darkrockstudios.apps.hammer.common.data.references.ImageUsage
import com.darkrockstudios.apps.hammer.common.data.references.ImageUsages
import com.darkrockstudios.apps.hammer.common.data.references.TouchedEntity
import com.darkrockstudios.apps.hammer.common.images.RenameImageCard
import com.darkrockstudios.apps.hammer.common.images.usageMessage
import com.darkrockstudios.apps.hammer.images_gallery_cancel
import com.darkrockstudios.apps.hammer.images_gallery_delete
import com.darkrockstudios.apps.hammer.images_gallery_delete_title
import com.darkrockstudios.apps.hammer.base.http.EntityType
import com.darkrockstudios.apps.hammer.confirm_kind_delete

/** Renaming an image, over a scrim. */
@Composable
internal fun RenameImageScrimPreview() {
	Scrimmed {
		RenameImageCard(name = "Harbour at dusk", onNameChange = {}, busy = false, onRename = {}, onCancel = {})
	}
}

/** Deleting an image a scene and an entry use, over a scrim. */
@Composable
internal fun DeleteImageScrimPreview() {
	val usages = ImageUsages(
		listOf(
			ImageUsage(ImageReferrer.Entity(TouchedEntity(EntityType.Scene, 7)), "The Lamp Room"),
			ImageUsage(ImageReferrer.Entity(TouchedEntity(EntityType.EncyclopediaEntry, 4)), "The great lamp"),
			ImageUsage(ImageReferrer.ProjectCover, "Cover"),
		)
	)
	Scrimmed {
		IndexStripCard(
			title = Res.string.images_gallery_delete_title.get("Harbour at dusk"),
			message = usageMessage(usages),
			destructive = true,
			kind = Res.string.confirm_kind_delete.get(),
		) {
			ConfirmationActions(
				confirmLabel = Res.string.images_gallery_delete.get(),
				onConfirm = {},
				cancelLabel = Res.string.images_gallery_cancel.get(),
				onCancel = {},
				destructive = true,
			)
		}
	}
}

@Composable
private fun Scrimmed(content: @Composable () -> Unit) {
	Box(
		modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f)),
		contentAlignment = Alignment.Center,
	) {
		content()
	}
}
