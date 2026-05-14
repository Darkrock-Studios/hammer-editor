package com.darkrockstudios.apps.hammer.common.timeline

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.Dp
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.common.TextEditorDefaults
import com.darkrockstudios.apps.hammer.common.components.timeline.CreateTimeLineEvent
import com.darkrockstudios.apps.hammer.common.compose.RootSnackbarHostState
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.designsystem.*
import com.darkrockstudios.apps.hammer.common.compose.markdowneditor.MarkdownEditField
import com.darkrockstudios.apps.hammer.common.compose.rememberStrRes
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEventError
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun CreateTimeLineEventUi(
	component: CreateTimeLineEvent,
	scope: CoroutineScope,
	modifier: Modifier,
	rootSnackbar: RootSnackbarHostState,
) {
	val strRes = rememberStrRes()
	var dateText by remember { mutableStateOf("") }
	var contentText by remember { mutableStateOf("") }
	var resetVersion by remember { mutableStateOf(0) }
	val tags = remember { mutableStateListOf<String>() }

	val wordCount = remember(contentText) {
		contentText.split(Regex("\\s+")).count { it.isNotBlank() }
	}
	val charCount = contentText.length

	Card(
		modifier = modifier.padding(Ui.Padding.XL)
			.widthIn(max = TextEditorDefaults.MAX_WIDTH * 1.25f),
		elevation = CardDefaults.elevatedCardElevation(Ui.Elevation.SMALL),
		shape = RectangleShape,
	) {
		Column(modifier = Modifier.fillMaxWidth()) {

			Column(
				modifier = Modifier
					.fillMaxWidth()
					.padding(
						start = Ui.Padding.XL,
						end = Ui.Padding.XL,
						top = Ui.Padding.XL,
						bottom = Ui.Padding.L,
					),
				verticalArrangement = Arrangement.spacedBy(Ui.Padding.M),
			) {
				HdSectionHeader(
					marker = "III · NEW",
					title = Res.string.timeline_create_header.get(),
					trailing = {
						HdMonoLabel(text = "DRAFT")
					},
				)
				HdMonoLabel(
					text = Res.string.timeline_create_body_hint.get(),
					color = MaterialTheme.colorScheme.onSurfaceVariant,
				)
			}

			HorizontalDivider(
				thickness = Dp.Hairline,
				color = MaterialTheme.colorScheme.outlineVariant,
			)

			Column(
				modifier = Modifier
					.fillMaxWidth()
					.padding(horizontal = Ui.Padding.XL, vertical = Ui.Padding.L),
				verticalArrangement = Arrangement.spacedBy(Ui.Padding.L),
			) {
				HdHairlineField(
					label = Res.string.timeline_view_date_label.get(),
					value = dateText,
					onValueChange = { dateText = it },
					hint = Res.string.timeline_create_date_hint.get(),
					placeholder = Res.string.timeline_create_date_placeholder.get(),
				)

				HdHairlineTagField(
					label = Res.string.timeline_create_tags_label.get(),
					tags = tags,
					onTagsChange = {
						tags.clear()
						tags.addAll(it)
					},
					hint = Res.string.timeline_create_tags_hint.get(),
					placeholder = Res.string.timeline_create_tags_placeholder.get(),
					suggestTags = component::suggestTags,
				)
			}

			Box(
				modifier = Modifier
					.padding(horizontal = Ui.Padding.XL)
					.padding(bottom = Ui.Padding.L)
					.fillMaxWidth()
					.weight(1f, fill = true)
					.border(
						width = Dp.Hairline,
						color = MaterialTheme.colorScheme.outlineVariant,
						shape = RectangleShape,
					),
			) {
				key(resetVersion) {
					MarkdownEditField(
						initialMarkdown = contentText,
						onMarkdownChanged = { contentText = it },
						contentPadding = PaddingValues(Ui.Padding.XL),
						modifier = Modifier
							.fillMaxWidth()
							.widthIn(max = TextEditorDefaults.MAX_WIDTH),
					)
				}
			}

			HorizontalDivider(
				thickness = Dp.Hairline,
				color = MaterialTheme.colorScheme.outlineVariant,
			)

			Row(
				modifier = Modifier
					.fillMaxWidth()
					.padding(horizontal = Ui.Padding.XL, vertical = Ui.Padding.L),
				horizontalArrangement = Arrangement.spacedBy(Ui.Padding.L),
				verticalAlignment = Alignment.CenterVertically,
			) {
				HdMonoLabel(text = "$wordCount W · $charCount CH")
				Spacer(modifier = Modifier.weight(1f))
				HdHairlineButton(
					label = Res.string.timeline_create_cancel_button.get(),
					onClick = { component.closeCreation() },
				)
				HdHairlineButton(
					label = Res.string.timeline_create_create_button.get(),
					emphasised = contentText.isNotBlank(),
					onClick = {
						scope.launch {
							when (component.createEvent(dateText, contentText, tags.toSet())) {
								TimeLineEventError.NONE -> {
									launch {
										rootSnackbar.showSnackbar(strRes.get(Res.string.timeline_create_toast_success))
									}
									dateText = ""
									contentText = ""
									tags.clear()
									resetVersion++
									component.closeCreation()
								}

								TimeLineEventError.TAG_TOO_LONG -> {
									launch {
										rootSnackbar.showSnackbar(
											strRes.get(
												Res.string.timeline_create_toast_tag_too_long,
												TimeLineRepository.MAX_TAG_SIZE,
											)
										)
									}
								}
							}
						}
					},
				)
			}
		}
	}
}
