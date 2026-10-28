package com.darkrockstudios.apps.hammer.common.projecthome

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdEngravingPlaceholder
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineButton
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineSection
import com.darkrockstudios.apps.hammer.common.compose.images.COVER_ASPECT_RATIO
import com.darkrockstudios.apps.hammer.common.compose.images.CoverThumbnail
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.image.ImageFile
import com.darkrockstudios.apps.hammer.common.images.ImageViewerDialog
import com.darkrockstudios.apps.hammer.common.images.ImageViewerItem
import com.darkrockstudios.apps.hammer.project_info_cover_caption
import com.darkrockstudios.apps.hammer.project_info_cover_clear
import com.darkrockstudios.apps.hammer.project_info_cover_missing
import com.darkrockstudios.apps.hammer.project_info_cover_none
import com.darkrockstudios.apps.hammer.project_info_cover_pick
import com.darkrockstudios.apps.hammer.project_info_cover_section_title

const val PROJECT_SETTINGS_COVER_PICK_TAG = "project-settings-cover-pick"
const val PROJECT_SETTINGS_COVER_CLEAR_TAG = "project-settings-cover-clear"
const val PROJECT_SETTINGS_COVER_THUMB_TAG = "project-settings-cover-thumb"

/**
 * The book cover: a thumbnail that opens the viewer, PICK (from the gallery) and CLEAR. A cover
 * whose image this device does not have yet shows a placeholder until a sync brings it.
 */
@Composable
internal fun CoverImageSection(
	section: Int,
	coverImageId: Int?,
	coverImage: ImageFile?,
	pickPending: Boolean,
	onPick: () -> Unit,
	onClear: () -> Unit,
	modifier: Modifier = Modifier,
) {
	var viewing by remember { mutableStateOf(false) }

	HdHairlineSection(
		modifier = modifier,
		section = section,
		title = Res.string.project_info_cover_section_title.get(),
		contentSpacing = 18.dp,
	) {
		Row(
			modifier = Modifier.fillMaxWidth(),
			horizontalArrangement = Arrangement.spacedBy(24.dp),
		) {
			val frame = Modifier
				.width(CoverWidth)
				.aspectRatio(COVER_ASPECT_RATIO)
				.testTag(PROJECT_SETTINGS_COVER_THUMB_TAG)
			if (coverImage != null) {
				CoverThumbnail(
					path = coverImage.path.path,
					version = coverImage.contentHash,
					contentDescription = coverImage.def.name,
					modifier = frame.clickable { viewing = true },
				)
			} else {
				val label = if (coverImageId == null) {
					Res.string.project_info_cover_none.get()
				} else {
					Res.string.project_info_cover_missing.get()
				}
				HdEngravingPlaceholder(label = label.uppercase(), modifier = frame)
			}

			Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
				Text(
					text = Res.string.project_info_cover_caption.get(),
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
				)
				HdHairlineButton(
					label = Res.string.project_info_cover_pick.get().uppercase(),
					onClick = onPick,
					enabled = !pickPending,
					emphasised = true,
					modifier = Modifier.testTag(PROJECT_SETTINGS_COVER_PICK_TAG),
				)
				if (coverImageId != null) {
					HdHairlineButton(
						label = Res.string.project_info_cover_clear.get().uppercase(),
						onClick = onClear,
						modifier = Modifier.testTag(PROJECT_SETTINGS_COVER_CLEAR_TAG),
					)
				}
			}
		}
	}

	if (viewing && coverImage != null) {
		ImageViewerDialog(
			items = listOf(
				ImageViewerItem.ofFile(
					path = coverImage.path.path,
					version = coverImage.contentHash,
					label = coverImage.def.name,
				)
			),
			initialIndex = 0,
			onDismissed = { viewing = false },
		)
	}
}

private val CoverWidth = 120.dp
