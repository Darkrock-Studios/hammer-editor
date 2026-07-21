package com.darkrockstudios.apps.hammer.common.encyclopedia

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Toys
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.components.encyclopedia.BrowseEntries
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMonoLabel
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdTagChip
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdTypeStamp
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdTypographicHero
import com.darkrockstudios.apps.hammer.common.compose.markdowneditor.MarkdownView
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

private val HeroHeight = 200.dp

fun encyclopediaEntryTag(id: Int) = "encyclopedia-entry-$id"

internal fun getEntryTypeIcon(type: EntryType): ImageVector {
	return when (type) {
		EntryType.PERSON -> Icons.Filled.Person
		EntryType.PLACE -> Icons.Filled.Place
		EntryType.THING -> Icons.Filled.Toys
		EntryType.EVENT -> Icons.Filled.Event
		EntryType.IDEA -> Icons.Filled.Lightbulb
	}
}

@OptIn(
	ExperimentalLayoutApi::class,
	ExperimentalMaterial3Api::class,
	ExperimentalSharedTransitionApi::class,
)
@Composable
internal fun EncyclopediaEntryItem(
	entryDef: EntryDef,
	component: BrowseEntries,
	viewEntry: (EntryDef) -> Unit,
	scope: CoroutineScope,
	sharedTransitionScope: SharedTransitionScope,
	animatedVisibilityScope: AnimatedVisibilityScope,
	modifier: Modifier = Modifier,
	tagsScrollHorizontally: Boolean = false,
	activeTags: Set<String> = emptySet(),
	filterByType: (type: EntryType) -> Unit,
) {
	val ioDispatcher = rememberIoDispatcher()
	val mainDispatcher = rememberMainDispatcher()
	var loadContentJob = remember<Job?> { null }
	var entryContent by remember { mutableStateOf<EntryContent?>(null) }
	var entryImagePath by remember { mutableStateOf<String?>(null) }
	var entryImageHash by remember { mutableStateOf<String?>(null) }
	var hasImage by remember { mutableStateOf<Boolean?>(null) }

	val paletteState = rememberPaletteState(loader = FilePathLoader)

	LaunchedEffect(entryDef) {
		entryImagePath = null
		entryImageHash = null
		hasImage = null
		paletteState.reset()
		loadContentJob?.cancel()
		loadContentJob = scope.launch(ioDispatcher) {
			val imagePath = component.getImagePath(entryDef)
			// The image's filename is stable across replacement, so the cache key must be unique
			val imageHash = imagePath?.let { component.calculateEntryImageHash(entryDef) }
			val content = component.loadEntryContent(entryDef)
			withContext(mainDispatcher) {
				entryImagePath = imagePath
				entryImageHash = imageHash
				hasImage = imagePath != null
				entryContent = content
				loadContentJob = null
			}
		}
	}

	val ruleColor = MaterialTheme.colorScheme.outlineVariant
	val ruleSoft = ruleColor.copy(alpha = 0.5f)

	with(sharedTransitionScope) {
		Column(
			modifier = modifier
				.fillMaxWidth()
				.background(MaterialTheme.colorScheme.surfaceContainerLow)
				.border(width = Dp.Hairline, color = ruleColor, shape = RectangleShape)
				.sharedElement(
					sharedContentState = rememberSharedContentState(
						key = "encyclopedia-card-${entryDef.id}",
					),
					animatedVisibilityScope = animatedVisibilityScope,
				)
				.testTag(encyclopediaEntryTag(entryDef.id))
				.clickable { viewEntry(entryDef) },
		) {
			// Hero zone — image (full-bleed with palette gradient) or
			// HdTypographicHero (name-as-art) when no image is set.
			Box(
				modifier = Modifier
					.fillMaxWidth()
					.height(HeroHeight),
			) {
				when (hasImage) {
					true -> {
						val palette = paletteState.palette
						val defaultStart = MaterialTheme.colorScheme.surfaceVariant
						val defaultEnd = MaterialTheme.colorScheme.surface
						val targetStart = palette?.dominantSwatch?.color
							?: palette?.vibrantSwatch?.color
							?: defaultStart
						val targetEnd = palette?.mutedSwatch?.color
							?: palette?.darkMutedSwatch?.color
							?: defaultEnd
						val gradientStart by animateColorAsState(
							targetValue = targetStart,
							animationSpec = tween(durationMillis = 400),
							label = "gradientStart",
						)
						val gradientEnd by animateColorAsState(
							targetValue = targetEnd,
							animationSpec = tween(durationMillis = 400),
							label = "gradientEnd",
						)
						val imagePath = entryImagePath
						if (imagePath != null) {
							with(animatedVisibilityScope) {
								Box(
									modifier = Modifier
										.fillMaxSize()
										.animateEnterExit(enter = fadeIn(), exit = fadeOut())
										.background(
											brush = Brush.verticalGradient(
												colors = listOf(gradientStart, gradientEnd),
											),
										),
								)
							}

							val context = LocalPlatformContext.current
							with(animatedVisibilityScope) {
								AsyncImage(
									model = remember(imagePath, entryImageHash) {
										ImageRequest.Builder(context)
											.data(imagePath)
											.memoryCacheKeyExtras(
												mapOf("hash" to entryImageHash.toString()),
											)
											.placeholderMemoryCacheKey(imagePath)
											.crossfade(300)
											.build()
									},
									contentDescription = null,
									modifier = Modifier
										.fillMaxSize()
										.sharedElement(
											sharedContentState = rememberSharedContentState(
												key = "encyclopedia-image-${entryDef.id}",
											),
											animatedVisibilityScope = animatedVisibilityScope,
										)
										.animateEnterExit(enter = fadeIn(), exit = fadeOut()),
									contentScale = ContentScale.Fit,
								)
							}
						} else {
							Box(
								modifier = Modifier.fillMaxSize(),
								contentAlignment = Alignment.Center,
							) {
								CircularProgressIndicator()
							}
						}
					}

					false -> HdTypographicHero(
						name = entryDef.name,
						type = entryDef.type,
						modifier = Modifier.fillMaxSize(),
						height = HeroHeight,
					)

					null -> Box(
						modifier = Modifier.fillMaxSize(),
						contentAlignment = Alignment.Center,
					) {
						CircularProgressIndicator()
					}
				}

				// Postage-stamp filter affordance — top-left of the
				// hero, overlaying both image and typographic variants.
				Box(
					modifier = Modifier
						.align(Alignment.TopStart)
						.padding(10.dp)
						.sharedElement(
							sharedContentState = rememberSharedContentState(
								key = "encyclopedia-chip-${entryDef.id}",
							),
							animatedVisibilityScope = animatedVisibilityScope,
						),
				) {
					HdTypeStamp(
						type = entryDef.type,
						label = entryDef.type.toStringResource().get().uppercase(),
						onClick = { filterByType(entryDef.type) },
					)
				}
			}

			HorizontalDivider(thickness = Dp.Hairline, color = ruleSoft)

			// Body — title (only when image hero — the typographic hero
			// already shows the name) and the description.
			Column(modifier = Modifier.padding(horizontal = 16.dp)) {
				if (hasImage == true) {
					Text(
						text = entryDef.name,
						style = MaterialTheme.typography.titleLarge,
						fontWeight = FontWeight.Normal,
						color = MaterialTheme.colorScheme.onSurface,
						maxLines = 2,
						overflow = TextOverflow.Ellipsis,
						modifier = Modifier
							.padding(top = 14.dp, bottom = 6.dp)
							.sharedElement(
								sharedContentState = rememberSharedContentState(
									key = "encyclopedia-title-${entryDef.id}",
								),
								animatedVisibilityScope = animatedVisibilityScope,
							),
					)
				}

				val content = entryContent
				if (loadContentJob != null && content == null) {
					Box(
						modifier = Modifier
							.fillMaxWidth()
							.padding(vertical = 14.dp),
						contentAlignment = Alignment.Center,
					) {
						CircularProgressIndicator()
					}
				} else if (content != null) {
					val previewMarkdown = remember(content.text) { content.text.trim() }
					MarkdownView(
						markdown = previewMarkdown,
						modifier = Modifier
							.fillMaxWidth()
							.padding(
								top = if (hasImage == true) 0.dp else 14.dp,
								bottom = 12.dp,
							)
							.sharedElement(
								sharedContentState = rememberSharedContentState(
									key = "encyclopedia-text-${entryDef.id}",
								),
								animatedVisibilityScope = animatedVisibilityScope,
							),
					)
				} else {
					Text(
						text = Res.string.encyclopedia_entry_load_error.get(),
						style = MaterialTheme.typography.bodyMedium,
						color = MaterialTheme.colorScheme.error,
						modifier = Modifier.padding(vertical = 12.dp),
					)
				}
			}

			// Tags — wrap row on wide cards, horizontally scrolling row
			// on mobile so the meta footer stays readable.
			val tags = entryContent?.tags.orEmpty()
			if (tags.isNotEmpty()) {
				if (tagsScrollHorizontally) {
					Row(
						modifier = Modifier
							.fillMaxWidth()
							.horizontalScroll(rememberScrollState())
							.padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
						horizontalArrangement = Arrangement.spacedBy(6.dp),
					) {
						tags.forEach { tag ->
							HdTagChip(
								label = tag,
								active = tag in activeTags,
								onClick = { component.addTagToSearch(tag) },
							)
						}
					}
				} else {
					FlowRow(
						modifier = Modifier
							.fillMaxWidth()
							.padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
						horizontalArrangement = Arrangement.spacedBy(6.dp),
						verticalArrangement = Arrangement.spacedBy(6.dp),
					) {
						tags.forEach { tag ->
							HdTagChip(
								label = tag,
								active = tag in activeTags,
								onClick = { component.addTagToSearch(tag) },
							)
						}
					}
				}
			} else {
				Spacer(modifier = Modifier.size(4.dp))
			}

			HorizontalDivider(thickness = Dp.Hairline, color = ruleSoft)

			// Footer — mono meta on the left, OPEN affordance on the
			// right. The card itself is the click target; this label
			// is just a typographic affordance, not a separate button.
			Row(
				modifier = Modifier
					.fillMaxWidth()
					.padding(horizontal = 12.dp, vertical = 8.dp),
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.SpaceBetween,
			) {
				val wordCount = entryContent?.text?.let { wordCount(it) } ?: 0
				val tagCount = tags.size
				HdMonoLabel(
					text = formatEntryMeta(wordCount = wordCount, tagCount = tagCount),
					color = MaterialTheme.colorScheme.onSurfaceVariant,
				)
				HdMonoLabel(
					text = "↗ OPEN",
					color = MaterialTheme.colorScheme.onSurfaceVariant,
				)
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
					),
				),
			),
	)
}

private fun wordCount(text: String): Int {
	if (text.isBlank()) return 0
	return text.trim().split(Regex("\\s+")).size
}

private fun formatEntryMeta(wordCount: Int, tagCount: Int): String {
	return buildString {
		append(wordCount)
		append(" W")
		if (tagCount > 0) {
			append(" · ")
			append(tagCount)
			append(" TAG")
			if (tagCount != 1) append("S")
		}
	}
}
