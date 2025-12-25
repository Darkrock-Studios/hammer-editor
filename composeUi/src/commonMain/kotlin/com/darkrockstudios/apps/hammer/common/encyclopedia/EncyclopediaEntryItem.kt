package com.darkrockstudios.apps.hammer.common.encyclopedia

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.components.encyclopedia.BrowseEntries
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.rememberIoDispatcher
import com.darkrockstudios.apps.hammer.common.compose.rememberMainDispatcher
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContent
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.encyclopedia_entry_load_error
import com.kmpalette.color
import com.kmpalette.loader.FilePathLoader
import com.kmpalette.rememberPaletteState
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

	var hasImage by remember { mutableStateOf<Boolean?>(null) }

	val paletteState = rememberPaletteState(loader = FilePathLoader)

	LaunchedEffect(entryDef) {
		entryImagePath = null
		hasImage = null
		paletteState.reset()
		loadContentJob?.cancel()
		loadContentJob = scope.launch(ioDispatcher) {
			val imagePath = component.getImagePath(entryDef)
			val content = component.loadEntryContent(entryDef)
			withContext(mainDispatcher) {
				entryImagePath = imagePath
				hasImage = imagePath != null
				entryContent = content
				loadContentJob = null
			}
		}
	}

	LaunchedEffect(entryImagePath) {
		val path = entryImagePath
		if (path != null) {
			// TODO: This is too perfy, I like the effect, but disable for now
			//paletteState.generate(path)
		}
	}

	with(sharedTransitionScope) {
		Card(
			modifier = modifier
				.fillMaxWidth()
				.padding(Ui.Padding.XL)
				.sharedElement(
					sharedContentState = rememberSharedContentState(key = "encyclopedia-card-${entryDef.id}"),
					animatedVisibilityScope = animatedVisibilityScope
				)
				.clickable { viewEntry(entryDef) },
			elevation = CardDefaults.elevatedCardElevation(Ui.Elevation.SMALL)
		) {
			Column(modifier = Modifier.fillMaxWidth()) {

				//  (hasImage == null) means we're loading, false means no image
				if (hasImage != false) {
					val palette = paletteState.palette
					val defaultStartColor = MaterialTheme.colorScheme.surfaceVariant
					val defaultEndColor = MaterialTheme.colorScheme.surface

					val targetStartColor = palette?.dominantSwatch?.color
						?: palette?.vibrantSwatch?.color
						?: defaultStartColor
					val targetEndColor = palette?.mutedSwatch?.color
						?: palette?.darkMutedSwatch?.color
						?: defaultEndColor

					// Animate color transitions for smooth fade-in
					val gradientStartColor by animateColorAsState(
						targetValue = targetStartColor,
						animationSpec = tween(durationMillis = 400),
						label = "gradientStartColor"
					)
					val gradientEndColor by animateColorAsState(
						targetValue = targetEndColor,
						animationSpec = tween(durationMillis = 400),
						label = "gradientEndColor"
					)

					Box(
						modifier = Modifier
							.fillMaxWidth()
							.height(256.dp)
							.clip(MaterialTheme.shapes.medium)
					) {
						if (entryImagePath != null) {
							with(animatedVisibilityScope) {
								// Background: gradient using dominant colors from the image
								Box(
									modifier = Modifier
										.matchParentSize()
										.animateEnterExit(
											enter = fadeIn(),
											exit = fadeOut()
										)
										.background(
											brush = Brush.verticalGradient(
												colors = listOf(gradientStartColor, gradientEndColor)
											)
										)
								)

								GradientDivider(
									modifier = Modifier
										.height(20.dp)
										.align(Alignment.BottomStart)
										.animateEnterExit(
											enter = fadeIn(),
											exit = fadeOut()
										)
								)
							}

							// Foreground: fitted image
							val context = LocalPlatformContext.current
							with(animatedVisibilityScope) {
								AsyncImage(
									model = remember(entryImagePath) {
										ImageRequest.Builder(context)
											.data(entryImagePath)
											.memoryCacheKey(entryImagePath)
											.placeholderMemoryCacheKey(entryImagePath)
											.crossfade(300)
											.build()
									},
									contentDescription = null,
									modifier = Modifier
										.align(Alignment.Center)
										.animateEnterExit(
											enter = fadeIn(),
											exit = fadeOut()
										)
										.clip(MaterialTheme.shapes.medium),
									contentScale = ContentScale.Fit
								)
							}
						} else {
							// Loading placeholder - keeps consistent height
							Box(
								modifier = Modifier.fillMaxSize(),
								contentAlignment = Alignment.Center
							) {
								CircularProgressIndicator()
							}
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
							modifier = Modifier
								.align(Alignment.BottomEnd)
								.padding(end = Ui.Padding.L)
								.sharedElement(
									sharedContentState = rememberSharedContentState(key = "encyclopedia-chip-${entryDef.id}"),
									animatedVisibilityScope = animatedVisibilityScope
								),
						)
					}
				} else {
					// No image - just show the chip
					AssistChip(
						onClick = { filterByType(entryDef.type) },
						label = { Text(entryDef.type.toStringResource().get()) },
						leadingIcon = {
							Icon(
								getEntryTypeIcon(entryDef.type),
								entryDef.type.toStringResource().get()
							)
						},
						modifier = Modifier
							.align(Alignment.End)
							.padding(end = Ui.Padding.L)
							.sharedElement(
								sharedContentState = rememberSharedContentState(key = "encyclopedia-chip-${entryDef.id}"),
								animatedVisibilityScope = animatedVisibilityScope
							)
					)
				}

				Column(
					modifier = Modifier
						.padding(
							top = Ui.Padding.L,
							start = Ui.Padding.L,
							end = Ui.Padding.L
						)
						.heightIn(min = 120.dp)
				) {
					Text(
						entryDef.name,
						style = MaterialTheme.typography.headlineMedium,
						maxLines = 2,
						overflow = TextOverflow.Ellipsis,
						modifier = Modifier.sharedElement(
							sharedContentState = rememberSharedContentState(key = "encyclopedia-title-${entryDef.id}"),
							animatedVisibilityScope = animatedVisibilityScope
						)
					)

					if (loadContentJob != null) {
						CircularProgressIndicator()
					} else {
						val content = entryContent
						if (content != null) {
							Text(
								content.text,
								style = MaterialTheme.typography.bodyMedium,
								maxLines = 3,
								overflow = TextOverflow.Ellipsis,
								modifier = Modifier.sharedElement(
									sharedContentState = rememberSharedContentState(key = "encyclopedia-text-${entryDef.id}"),
									animatedVisibilityScope = animatedVisibilityScope
								)
							)

							Spacer(modifier = Modifier.size(Ui.Padding.L))

							FlowRow(
								modifier = Modifier.heightIn(max = 40.dp),
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