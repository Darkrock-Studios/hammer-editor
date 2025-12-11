package com.darkrockstudios.apps.hammer.common.encyclopedia

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.components.encyclopedia.BrowseEntries
import com.darkrockstudios.apps.hammer.common.compose.ImageItem
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.rememberIoDispatcher
import com.darkrockstudios.apps.hammer.common.compose.rememberMainDispatcher
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContent
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.encyclopedia_entry_load_error
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun getEntryTypeIcon(type: EntryType): ImageVector {
	return when (type) {
		EntryType.PERSON -> Icons.Filled.Person
		EntryType.PLACE -> Icons.Filled.Place
		EntryType.THING -> Icons.Filled.Toys
		EntryType.EVENT -> Icons.Filled.Event
		EntryType.IDEA -> Icons.Filled.Lightbulb
	}
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
internal fun EncyclopediaEntryItem(
	entryDef: EntryDef,
	component: BrowseEntries,
	viewEntry: (EntryDef) -> Unit,
	scope: CoroutineScope,
	sharedTransitionScope: SharedTransitionScope,
	animatedVisibilityScope: AnimatedVisibilityScope,
	modifier: Modifier = Modifier,
	filterByType: (type: EntryType) -> Unit
) {
	val ioDispatcher = rememberIoDispatcher()
	val mainDispatcher = rememberMainDispatcher()
	var loadContentJob = remember<Job?> { null }
	var entryContent by remember { mutableStateOf<EntryContent?>(null) }
	var entryImagePath by remember { mutableStateOf<String?>(null) }

	LaunchedEffect(entryDef) {
		entryImagePath = null
		loadContentJob?.cancel()
		loadContentJob = scope.launch(ioDispatcher) {
			val imagePath = component.getImagePath(entryDef)
			val content = component.loadEntryContent(entryDef)
			withContext(mainDispatcher) {
				entryImagePath = imagePath
				entryContent = content
				loadContentJob = null
			}
		}
	}

	Card(
		modifier = modifier
			.fillMaxWidth()
			.padding(Ui.Padding.XL)
			.clickable { viewEntry(entryDef) },
		elevation = CardDefaults.elevatedCardElevation(Ui.Elevation.SMALL)
	) {
		Column(modifier = Modifier.fillMaxWidth()) {

			if (entryImagePath != null) {
				Box(
					modifier = Modifier
						.fillMaxWidth()
						.heightIn(max = 256.dp)
						.clip(MaterialTheme.shapes.medium)
				) {
					// Background: blurred, cropped image to fill empty space
					ImageItem(
						path = entryImagePath,
						modifier = Modifier
							.matchParentSize()
							.blur(radius = 25.dp)
							.alpha(0.6f),
						contentScale = ContentScale.Crop
					)

					GradientDivider(modifier = Modifier.height(20.dp).align(Alignment.BottomStart))

					// Foreground: fitted image with shared element transition
					with(sharedTransitionScope) {
						ImageItem(
							path = entryImagePath,
							modifier = Modifier
								.align(Alignment.Center)
								.sharedElement(
									sharedContentState = rememberSharedContentState(key = "encyclopedia-image-${entryDef.id}"),
									animatedVisibilityScope = animatedVisibilityScope
								)
								.clip(MaterialTheme.shapes.medium),
							contentScale = ContentScale.Fit
						)
					}

					AssistChip(
						onClick = { filterByType(entryDef.type) },
						label = { Text(entryDef.type.toStringResource().get()) },
						leadingIcon = {
							Icon(
								getEntryTypeIcon(entryDef.type),
								entryDef.type.toStringResource().get()
							)
						},
						modifier = Modifier.align(Alignment.BottomEnd).padding(end = Ui.Padding.L),
						colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
					)
				}
			} else {
				AssistChip(
					onClick = { filterByType(entryDef.type) },
					label = { Text(entryDef.type.toStringResource().get()) },
					leadingIcon = {
						Icon(
							getEntryTypeIcon(entryDef.type),
							entryDef.type.toStringResource().get()
						)
					},
					modifier = Modifier.align(Alignment.End).padding(end = Ui.Padding.L)
				)
			}

			Column(
				modifier = Modifier.padding(
					top = Ui.Padding.L,
					start = Ui.Padding.L,
					end = Ui.Padding.L
				)
			) {
				Text(
					entryDef.name,
					style = MaterialTheme.typography.headlineMedium
				)

				if (loadContentJob != null) {
					CircularProgressIndicator()
				} else {
					val content = entryContent
					if (content != null) {
						Text(
							content.text,
							style = MaterialTheme.typography.bodyMedium
						)

						Spacer(modifier = Modifier.size(Ui.Padding.L))

						FlowRow(
							modifier = modifier,
							horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
							verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Top),
						) {
							for (tag in content.tags) {
								InputChip(
									onClick = {
										component.addTagToSearch(tag)
									},
									label = { Text(tag) },
									leadingIcon = {
										Icon(
											Icons.Filled.Tag,
											contentDescription = null,
											tint = MaterialTheme.colorScheme.onSurface
										)
									},
									enabled = true,
									selected = false
								)
							}
						}
					} else {
						Text(Res.string.encyclopedia_entry_load_error.get())
					}
				}
			}
		}
	}
}

@Composable
fun GradientDivider(modifier: Modifier = Modifier) {
	Box(
		modifier = modifier
			.fillMaxWidth()
			.background(
				brush = Brush.verticalGradient(
					colors = listOf(
						Color.Transparent,
						Color(0xFF222222),
					)
				)
			)
	)
}