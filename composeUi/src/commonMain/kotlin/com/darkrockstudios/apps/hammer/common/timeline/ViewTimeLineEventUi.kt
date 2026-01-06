package com.darkrockstudios.apps.hammer.common.timeline

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
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
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.common.TextEditorDefaults
import com.darkrockstudios.apps.hammer.common.components.timeline.ViewTimeLineEvent
import com.darkrockstudios.apps.hammer.common.compose.*
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun ViewTimeLineEventUi(
	component: ViewTimeLineEvent,
	modifier: Modifier = Modifier,
	scope: CoroutineScope,
	rootSnackbar: RootSnackbarHostState,
	sharedTransitionScope: SharedTransitionScope,
	animatedVisibilityScope: AnimatedVisibilityScope,
) {
	val strRes = rememberStrRes()

	val dispatcherDefault = rememberDefaultDispatcher()
	val state by component.state.subscribeAsState()

	val dateText by component.dateText.subscribeAsState()
	val eventText by component.contentText.subscribeAsState()

	val event = remember(state.event) { state.event }

	Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
		with(sharedTransitionScope) {
			Card(
				modifier = Modifier
					.padding(Ui.Padding.XL)
					.widthIn(max = TextEditorDefaults.MAX_WIDTH * 1.25f)
					.sharedElement(
						sharedContentState = rememberSharedContentState(key = "timeline-card-${event?.id}"),
						animatedVisibilityScope = animatedVisibilityScope
					),
				elevation = CardDefaults.elevatedCardElevation(Ui.Elevation.SMALL)
			) {
			Column(
				modifier = Modifier.padding(Ui.Padding.XL).widthIn(Ui.DetailCard.MIN_WIDTH, Ui.DetailCard.MAX_WIDTH)
					.wrapContentHeight()
			) {
				Row(
					modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
					horizontalArrangement = Arrangement.End,
					verticalAlignment = Alignment.CenterVertically
				) {
					Text(
						Res.string.timeline_view_title.get(),
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
										rootSnackbar.showSnackbar(strRes.get(Res.string.timeline_view_toast_save_success))
									}
								} else {
									scope.launch {
										rootSnackbar.showSnackbar(strRes.get(Res.string.timeline_view_toast_save_failure))
									}
								}
							}
						}) {
							Icon(
								Icons.Filled.Check,
								Res.string.timeline_view_save_button.get(),
								tint = MaterialTheme.colorScheme.onSurface
							)
						}

						IconButton(onClick = {
							component.confirmDiscard()
						}) {
							Icon(
								Icons.Filled.Cancel,
								Res.string.timeline_view_cancel_button.get(),
								tint = MaterialTheme.colorScheme.error
							)
						}

						Spacer(modifier = Modifier.size(Ui.Padding.XL))

						HorizontalDivider(
							modifier = Modifier.fillMaxHeight().width(1.dp)
								.padding(top = Ui.Padding.M, bottom = Ui.Padding.M),
							thickness = DividerDefaults.Thickness,
							color = MaterialTheme.colorScheme.outline
						)

						Spacer(modifier = Modifier.size(Ui.Padding.XL))
					}

					DetailViewDropdownMenu(menuItems = state.menuItems)

					IconButton(
						onClick = { component.confirmClose() },
					) {
						Icon(
							Icons.Filled.Close,
							contentDescription = Res.string.timeline_view_close_button.get(),
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
								placeholder = { Text(Res.string.timeline_view_date_label.get()) }
							)
						} else {
							Text(
								date,
								style = MaterialTheme.typography.headlineMedium,
								color = MaterialTheme.colorScheme.onSurface,
								modifier = Modifier
									.wrapContentHeight()
									.fillMaxWidth()
									.sharedElement(
										sharedContentState = rememberSharedContentState(key = "timeline-date-${event.id}"),
										animatedVisibilityScope = animatedVisibilityScope
									)
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
							placeholder = { Text(text = Res.string.timeline_view_content_placeholder.get()) },
							maxLines = 10,
						)
					} else {
						Text(
							event.content,
							style = MaterialTheme.typography.bodyMedium,
							color = MaterialTheme.colorScheme.onSurface,
							modifier = Modifier
								.wrapContentHeight()
								.fillMaxWidth()
								.sharedElement(
									sharedContentState = rememberSharedContentState(key = "timeline-content-${event.id}"),
									animatedVisibilityScope = animatedVisibilityScope
								)
								.clickable { component.beginEdit() }
						)
					}
				}
			}
		}
	}
	}

	if (state.confirmClose) {
		SimpleConfirm(
			title = Res.string.timeline_view_discard_title.get(),
			message = Res.string.timeline_view_discard_message.get(),
			onDismiss = { component.cancelClose() }
		) {
			component.closeEvent()
		}
	}

	if (state.confirmDiscard) {
		SimpleConfirm(
			title = Res.string.timeline_view_discard_title.get(),
			message = Res.string.timeline_view_discard_message.get(),
			onDismiss = { component.cancelDiscard() }
		) {
			component.discardEdit()
		}
	}

	if (state.confirmDelete) {
		SimpleConfirm(
			title = Res.string.timeline_view_confirm_delete_title.get(),
			message = Res.string.timeline_view_confirm_delete_message.get(),
			onDismiss = { component.endDeleteEvent() }
		) {
			scope.launch {
				component.deleteEvent()
			}
		}
	}
}