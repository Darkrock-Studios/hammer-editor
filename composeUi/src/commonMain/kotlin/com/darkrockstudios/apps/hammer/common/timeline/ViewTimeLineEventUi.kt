package com.darkrockstudios.apps.hammer.common.timeline

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.MR
import com.darkrockstudios.apps.hammer.common.TextEditorDefaults
import com.darkrockstudios.apps.hammer.common.components.timeline.ViewTimeLineEvent
import com.darkrockstudios.apps.hammer.common.compose.*
import com.darkrockstudios.apps.hammer.common.compose.moko.get
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun ViewTimeLineEventUi(
	component: ViewTimeLineEvent,
	modifier: Modifier = Modifier,
	scope: CoroutineScope,
	rootSnackbar: RootSnackbarHostState,
) {
	val strRes = rememberStrRes()

	val dispatcherDefault = rememberDefaultDispatcher()
	val state by component.state.subscribeAsState()

	val dateText by component.dateText.subscribeAsState()
	val eventText by component.contentText.subscribeAsState()

	val event = remember(state.event) { state.event }

	Card(
		modifier = modifier.padding(Ui.Padding.XL)
			.widthIn(max = TextEditorDefaults.MAX_WIDTH * 1.25f),
		elevation = CardDefaults.elevatedCardElevation(Ui.Elevation.SMALL)
	) {
		Column(
			modifier = Modifier.padding(Ui.Padding.XL).widthIn(128.dp, 700.dp).wrapContentHeight()
		) {
			Row(
				modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
				horizontalArrangement = Arrangement.End,
				verticalAlignment = Alignment.CenterVertically
			) {
				Text(
					MR.strings.timeline_view_title.get(),
					modifier = Modifier.weight(1f),
					style = MaterialTheme.typography.displaySmall,
				)

				if (event != null && state.isEditing) {
					IconButton(onClick = {
						scope.launch(dispatcherDefault) {
							val success = component.storeEvent(
								event.copy(
									date = dateText,
									content = eventText
								)
							)

							if (success) {
								scope.launch {
									rootSnackbar.showSnackbar(strRes.get(MR.strings.timeline_view_toast_save_success))
								}
							} else {
								scope.launch {
									rootSnackbar.showSnackbar(strRes.get(MR.strings.timeline_view_toast_save_failure))
								}
							}
						}
					}) {
						Icon(
							Icons.Filled.Check,
							MR.strings.timeline_view_save_button.get(),
							tint = MaterialTheme.colorScheme.onSurface
						)
					}

					IconButton(onClick = {
						component.confirmDiscard()
					}) {
						Icon(
							Icons.Filled.Cancel,
							MR.strings.timeline_view_cancel_button.get(),
							tint = MaterialTheme.colorScheme.error
						)
					}

					Spacer(modifier = Modifier.size(Ui.Padding.XL))

					Divider(
						color = MaterialTheme.colorScheme.outline,
						modifier = Modifier.fillMaxHeight().width(1.dp)
							.padding(top = Ui.Padding.M, bottom = Ui.Padding.M)
					)

					Spacer(modifier = Modifier.size(Ui.Padding.XL))
				}

				ViewEventMenuUi(component)

				IconButton(
					onClick = { component.confirmClose() },
				) {
					Icon(
						Icons.Filled.Close,
						contentDescription = MR.strings.timeline_view_close_button.get(),
						tint = MaterialTheme.colorScheme.onSurface
					)
				}
			}

			if (event != null) {
				event.date?.let { date ->
					if (state.isEditing) {
						TextField(
							modifier = Modifier.wrapContentHeight().fillMaxWidth(),
							value = dateText,
							onValueChange = { component.onDateTextChanged(it) },
							placeholder = { Text(MR.strings.timeline_view_date_label.get()) }
						)
					} else {
						Text(
							date,
							style = MaterialTheme.typography.headlineMedium,
							color = MaterialTheme.colorScheme.onSurface,
							modifier = Modifier.wrapContentHeight().fillMaxWidth()
								.clickable { component.beginEdit() }
						)
					}
				}

				Spacer(modifier = Modifier.size(Ui.Padding.L))

				if (state.isEditing) {
					OutlinedTextField(
						value = eventText,
						onValueChange = { component.onEventTextChanged(it) },
						modifier = Modifier.fillMaxWidth()
							.padding(PaddingValues(bottom = Ui.Padding.XL)),
						placeholder = { Text(text = MR.strings.timeline_view_content_placeholder.get()) },
						maxLines = 10,
					)
				} else {
					Text(
						event.content,
						style = MaterialTheme.typography.bodyMedium,
						color = MaterialTheme.colorScheme.onSurface,
						modifier = Modifier.wrapContentHeight().fillMaxWidth()
							.clickable { component.beginEdit() }
					)
				}
			}
		}
	}

	if (state.confirmClose) {
		SimpleConfirm(
			title = MR.strings.timeline_view_discard_title.get(),
			message = MR.strings.timeline_view_discard_message.get(),
			onDismiss = { component.cancelClose() }
		) {
			component.closeEvent()
		}
	}

	if (state.confirmDiscard) {
		SimpleConfirm(
			title = MR.strings.timeline_view_discard_title.get(),
			message = MR.strings.timeline_view_discard_message.get(),
			onDismiss = { component.cancelDiscard() }
		) {
			component.discardEdit()
		}
	}

	if (state.confirmDelete) {
		SimpleConfirm(
			title = MR.strings.timeline_view_confirm_delete_title.get(),
			message = MR.strings.timeline_view_confirm_delete_message.get(),
			onDismiss = { component.endDeleteEvent() }
		) {
			scope.launch {
				component.deleteEvent()
			}
		}
	}
}