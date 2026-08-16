package com.darkrockstudios.apps.hammer.common.projectselection.storyideas

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.TextEditorDefaults
import com.darkrockstudios.apps.hammer.common.components.projectselection.storyideas.StoryIdeas
import com.darkrockstudios.apps.hammer.common.compose.CollapseWhileTyping
import com.darkrockstudios.apps.hammer.common.compose.DetailViewDropdownMenu
import com.darkrockstudios.apps.hammer.common.compose.LocalScreenCharacteristic
import com.darkrockstudios.apps.hammer.common.compose.MpScrollBarColumn
import com.darkrockstudios.apps.hammer.common.compose.MpScrollBarStaggeredGrid
import com.darkrockstudios.apps.hammer.common.compose.RootSnackbarHostState
import com.darkrockstudios.apps.hammer.common.compose.SimpleConfirm
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdActiveFiltersStrip
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdCollapsingStrip
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdCrumbBackLink
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdDetailStampRow
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdFab
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdFolioDivider
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineButton
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineField
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineTagField
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMarkdownCard
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMonoLabel
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdSearchRow
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdSectionHeader
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdSortMenu
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdSortOption
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdTagChip
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdTagFilterBar
import com.darkrockstudios.apps.hammer.common.compose.firstNonBlankLine
import com.darkrockstudios.apps.hammer.common.compose.markdowneditor.MarkdownEditField
import com.darkrockstudios.apps.hammer.common.compose.markdowneditor.MarkdownView
import com.darkrockstudios.apps.hammer.common.compose.rememberMainDispatcher
import com.darkrockstudios.apps.hammer.common.compose.rememberStrRes
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.compose.saveShortcutModifier
import com.darkrockstudios.apps.hammer.common.compose.scrollBarOverlay
import com.darkrockstudios.apps.hammer.common.data.MenuItemDescriptor
import com.darkrockstudios.apps.hammer.common.data.ideasrepository.IdeaError
import com.darkrockstudios.apps.hammer.common.data.ideasrepository.IdeasRepository
import com.darkrockstudios.apps.hammer.base.http.storyideas.StoryIdea
import com.darkrockstudios.apps.hammer.common.data.isSuccess
import com.darkrockstudios.apps.hammer.common.data.search.markdownContains
import com.darkrockstudios.apps.hammer.common.data.tagindex.TagCount
import com.darkrockstudios.apps.hammer.common.util.format
import com.darkrockstudios.apps.hammer.ideas_archive_button
import com.darkrockstudios.apps.hammer.ideas_cancel_button
import com.darkrockstudios.apps.hammer.ideas_create_button
import com.darkrockstudios.apps.hammer.ideas_create_fab
import com.darkrockstudios.apps.hammer.ideas_create_marker
import com.darkrockstudios.apps.hammer.ideas_delete_button
import com.darkrockstudios.apps.hammer.ideas_delete_dialog_message
import com.darkrockstudios.apps.hammer.ideas_delete_dialog_title
import com.darkrockstudios.apps.hammer.ideas_discard_dialog_message
import com.darkrockstudios.apps.hammer.ideas_discard_dialog_title
import com.darkrockstudios.apps.hammer.ideas_editor_counter
import com.darkrockstudios.apps.hammer.ideas_filter_all
import com.darkrockstudios.apps.hammer.ideas_filter_archived
import com.darkrockstudios.apps.hammer.ideas_filter_clear_all
import com.darkrockstudios.apps.hammer.ideas_filter_filtered
import com.darkrockstudios.apps.hammer.ideas_header
import com.darkrockstudios.apps.hammer.ideas_header_meta
import com.darkrockstudios.apps.hammer.ideas_list_empty
import com.darkrockstudios.apps.hammer.ideas_list_empty_archived
import com.darkrockstudios.apps.hammer.ideas_promote_button
import com.darkrockstudios.apps.hammer.ideas_promote_dialog_message
import com.darkrockstudios.apps.hammer.ideas_promote_dialog_title
import com.darkrockstudios.apps.hammer.ideas_save_button
import com.darkrockstudios.apps.hammer.ideas_search_button
import com.darkrockstudios.apps.hammer.ideas_search_clear
import com.darkrockstudios.apps.hammer.ideas_search_close
import com.darkrockstudios.apps.hammer.ideas_search_placeholder
import com.darkrockstudios.apps.hammer.ideas_sort_glyph_date_asc
import com.darkrockstudios.apps.hammer.ideas_sort_glyph_date_desc
import com.darkrockstudios.apps.hammer.ideas_sort_glyph_title_az
import com.darkrockstudios.apps.hammer.ideas_sort_label
import com.darkrockstudios.apps.hammer.ideas_sort_newest
import com.darkrockstudios.apps.hammer.ideas_sort_oldest
import com.darkrockstudios.apps.hammer.ideas_sort_title_az
import com.darkrockstudios.apps.hammer.ideas_stamp_archived
import com.darkrockstudios.apps.hammer.ideas_stamp_promoted
import com.darkrockstudios.apps.hammer.ideas_tags_hint
import com.darkrockstudios.apps.hammer.ideas_tags_label
import com.darkrockstudios.apps.hammer.ideas_tags_placeholder
import com.darkrockstudios.apps.hammer.ideas_title_label
import com.darkrockstudios.apps.hammer.ideas_title_placeholder
import com.darkrockstudios.apps.hammer.ideas_toast_archived
import com.darkrockstudios.apps.hammer.ideas_toast_unarchived
import com.darkrockstudios.apps.hammer.ideas_toast_created
import com.darkrockstudios.apps.hammer.ideas_toast_deleted
import com.darkrockstudios.apps.hammer.ideas_toast_promote_failed
import com.darkrockstudios.apps.hammer.ideas_toast_promoted
import com.darkrockstudios.apps.hammer.ideas_toast_empty
import com.darkrockstudios.apps.hammer.ideas_toast_saved
import com.darkrockstudios.apps.hammer.ideas_toast_tag_too_long
import com.darkrockstudios.apps.hammer.ideas_toast_too_long
import com.darkrockstudios.apps.hammer.ideas_unarchive_button
import com.darkrockstudios.apps.hammer.ideas_view_action_edit
import com.darkrockstudios.apps.hammer.ideas_view_close_button
import com.darkrockstudios.apps.hammer.ideas_view_crumb_root
import com.darkrockstudios.apps.hammer.ideas_view_header
import com.darkrockstudios.apps.hammer.ideas_view_label_editing
import com.darkrockstudios.apps.hammer.ideas_view_status_unsaved
import com.darkrockstudios.apps.hammer.ideas_tag_count_short
import com.darkrockstudios.apps.hammer.ideas_word_count_short
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource
import kotlin.time.Instant
import androidx.compose.foundation.lazy.staggeredgrid.items as staggeredItems

const val IDEAS_CREATE_FAB_TAG = "ideas-create-fab"
const val IDEAS_EDITOR_TITLE_TAG = "ideas-editor-title"
const val IDEAS_EDITOR_BODY_TAG = "ideas-editor-body"
const val IDEAS_EDITOR_CONFIRM_TAG = "ideas-editor-confirm"
const val IDEAS_EDITOR_CANCEL_TAG = "ideas-editor-cancel"
const val IDEAS_VIEW_EDIT_TAG = "ideas-view-edit"
fun ideaCardTag(id: String) = "idea-card-$id"

private val DetailMaxWidth = TextEditorDefaults.MAX_WIDTH * 1.25f
private val DetailMaxHeight = 760.dp

private enum class IdeasSortMode(
	override val labelRes: StringResource,
	override val glyphRes: StringResource,
) : HdSortOption {
	DateDesc(Res.string.ideas_sort_newest, Res.string.ideas_sort_glyph_date_desc),
	DateAsc(Res.string.ideas_sort_oldest, Res.string.ideas_sort_glyph_date_asc),
	TitleAsc(Res.string.ideas_sort_title_az, Res.string.ideas_sort_glyph_title_az),
}

private val IdeasSortModeSaver = Saver<IdeasSortMode, String>(
	save = { it.name },
	restore = { IdeasSortMode.valueOf(it) },
)

private val TagSetSaver = listSaver<Set<String>, String>(
	save = { it.toList() },
	restore = { it.toSet() },
)

private fun StoryIdea.wordCount(): Int =
	content.split(Regex("\\s+")).count { it.isNotBlank() }

/** An idea matches on its body read as prose, or on its plain-text title. */
internal fun ideaMatchesQuery(idea: StoryIdea, query: String): Boolean =
	markdownContains(idea.content, query) ||
		idea.title?.contains(query, ignoreCase = true) == true

private fun StoryIdea.displayTitle(): String = title ?: content.firstNonBlankLine()

private fun applySort(ideas: List<StoryIdea>, mode: IdeasSortMode): List<StoryIdea> = when (mode) {
	IdeasSortMode.DateDesc -> ideas.sortedByDescending { it.created }
	IdeasSortMode.DateAsc -> ideas.sortedBy { it.created }
	IdeasSortMode.TitleAsc -> ideas.sortedBy { it.displayTitle().lowercase() }
}

@Composable
fun StoryIdeasUi(
	component: StoryIdeas,
	rootSnackbar: RootSnackbarHostState,
	modifier: Modifier = Modifier,
) {
	val state by component.state.subscribeAsState()

	SharedTransitionLayout(modifier = modifier.fillMaxSize()) {
		AnimatedContent(
			targetState = state.editor,
			modifier = Modifier.fillMaxSize(),
			transitionSpec = { fadeIn() togetherWith fadeOut() },
			contentKey = { it != null },
			label = "StoryIdeasEditorSwap",
		) { editor ->
			val draft = state.draft
			if (editor != null && draft != null) {
				IdeaDetail(
					component = component,
					editor = editor,
					draft = draft,
					rootSnackbar = rootSnackbar,
					sharedTransitionScope = this@SharedTransitionLayout,
					animatedVisibilityScope = this@AnimatedContent,
				)
			} else {
				IdeasBrowse(
					component = component,
					ideas = state.ideas,
					sharedTransitionScope = this@SharedTransitionLayout,
					animatedVisibilityScope = this@AnimatedContent,
				)
			}
		}
	}
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
private fun IdeasBrowse(
	component: StoryIdeas,
	ideas: List<StoryIdea>,
	sharedTransitionScope: SharedTransitionScope,
	animatedVisibilityScope: AnimatedVisibilityScope,
) {
	val screen = LocalScreenCharacteristic.current
	val isWide = screen.isWide

	var searchQuery by rememberSaveable { mutableStateOf("") }
	var sortMode by rememberSaveable(stateSaver = IdeasSortModeSaver) {
		mutableStateOf(IdeasSortMode.DateDesc)
	}
	var activeTags by rememberSaveable(stateSaver = TagSetSaver) {
		mutableStateOf<Set<String>>(emptySet())
	}
	var showArchived by rememberSaveable { mutableStateOf(false) }
	var showSearchBar by rememberSaveable { mutableStateOf(false) }

	val scopedIdeas by remember(ideas, showArchived) {
		derivedStateOf {
			ideas.filter { (it.archived != null) == showArchived }
		}
	}

	val rankedTags by remember(scopedIdeas) {
		derivedStateOf {
			scopedIdeas
				.flatMap { it.tags }
				.groupingBy { it }
				.eachCount()
				.entries
				.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
				.map { TagCount(it.key, it.value) }
		}
	}

	val visibleIdeas by remember(scopedIdeas, searchQuery, sortMode, activeTags) {
		derivedStateOf {
			val query = searchQuery.trim()
			val byText = if (query.isBlank()) {
				scopedIdeas
			} else {
				scopedIdeas.filter { ideaMatchesQuery(it, query) }
			}
			val byTags = if (activeTags.isEmpty()) {
				byText
			} else {
				byText.filter { idea -> activeTags.all { it in idea.tags } }
			}
			applySort(byTags, sortMode)
		}
	}

	val toggleTag: (String) -> Unit = { tag ->
		activeTags = if (tag in activeTags) activeTags - tag else activeTags + tag
	}
	val clearTags: () -> Unit = { activeTags = emptySet() }

	val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(
		state = rememberTopAppBarState(),
	)

	Box(modifier = Modifier.fillMaxSize()) {
		Column(
			modifier = Modifier
				.fillMaxSize()
				.nestedScroll(scrollBehavior.nestedScrollConnection),
		) {
			AnimatedContent(
				targetState = showSearchBar && !isWide,
				modifier = Modifier
					.fillMaxWidth()
					.height(Ui.TOP_BAR_HEIGHT)
					.padding(horizontal = Ui.Padding.XL),
				transitionSpec = {
					fadeIn() togetherWith fadeOut() using SizeTransform(clip = false)
				},
				label = "IdeasTitleAnim",
			) { searching ->
				if (searching) {
					HdSearchRow(
						query = searchQuery,
						onQueryChange = { searchQuery = it },
						placeholder = Res.string.ideas_search_placeholder.get(),
						clearContentDescription = Res.string.ideas_search_clear.get(),
						onCollapse = {
							showSearchBar = false
							searchQuery = ""
						},
						collapseContentDescription = Res.string.ideas_search_close.get(),
						modifier = Modifier.fillMaxSize(),
					)
				} else {
					Row(
						modifier = Modifier.fillMaxSize(),
						verticalAlignment = Alignment.CenterVertically,
					) {
						HdSectionHeader(
							section = 2,
							title = Res.string.ideas_header.get(),
							modifier = Modifier.weight(1f),
							trailing = {
								if (isWide) {
									HdMonoLabel(
										text = Res.string.ideas_header_meta.get(
											ideas.size,
											rankedTags.size,
										),
									)
								}
							},
						)
						IconButton(
							onClick = {
								if (showSearchBar) {
									showSearchBar = false
									searchQuery = ""
								} else {
									showSearchBar = true
								}
							},
						) {
							Icon(
								imageVector = Icons.Default.Search,
								contentDescription = Res.string.ideas_search_button.get(),
								tint = MaterialTheme.colorScheme.onSurface,
							)
						}
					}
				}
			}

			HdFolioDivider()

			HdCollapsingStrip(scrollBehavior = scrollBehavior) {
				// On wide screens the search field is hidden until the masthead icon reveals
				// it; on compact the reveal swaps into the title row instead (above).
				if (isWide) {
					AnimatedVisibility(visible = showSearchBar) {
						Row(
							modifier = Modifier
								.fillMaxWidth()
								.padding(horizontal = Ui.Padding.XL, vertical = Ui.Padding.M),
							verticalAlignment = Alignment.CenterVertically,
						) {
							HdSearchRow(
								query = searchQuery,
								onQueryChange = { searchQuery = it },
								placeholder = Res.string.ideas_search_placeholder.get(),
								clearContentDescription = Res.string.ideas_search_clear.get(),
								onCollapse = {
									showSearchBar = false
									searchQuery = ""
								},
								collapseContentDescription = Res.string.ideas_search_close.get(),
								modifier = Modifier.fillMaxWidth(),
							)
						}
					}
				}

				HdTagFilterBar(
					tags = rankedTags,
					allLabel = "${Res.string.ideas_filter_all.get()} · ${scopedIdeas.size}",
					activeTags = activeTags,
					onToggle = toggleTag,
					onClear = clearTags,
					trailing = {
						val archivedCount = ideas.count { it.archived != null }
						ArchivedToggle(
							label = "${Res.string.ideas_filter_archived.get()} · $archivedCount",
							active = showArchived,
							onClick = {
								showArchived = !showArchived
								activeTags = emptySet()
							},
						)
						HdSortMenu(
							label = Res.string.ideas_sort_label,
							options = IdeasSortMode.entries,
							selected = sortMode,
							onSelect = { sortMode = it },
						)
					},
				)

				AnimatedVisibility(visible = activeTags.isNotEmpty()) {
					HdActiveFiltersStrip(
						activeTags = activeTags,
						filteredLabel = Res.string.ideas_filter_filtered.get(
							visibleIdeas.size,
							scopedIdeas.size,
						),
						clearAllLabel = Res.string.ideas_filter_clear_all.get(),
						onToggle = toggleTag,
						onClear = clearTags,
					)
				}

				HorizontalDivider(
					thickness = Dp.Hairline,
					color = MaterialTheme.colorScheme.outlineVariant,
				)
			}

			val gridState = rememberLazyStaggeredGridState()
			Box(modifier = Modifier.weight(1f)) {
				LazyVerticalStaggeredGrid(
					state = gridState,
					columns = StaggeredGridCells.Adaptive(400.dp),
					modifier = Modifier.fillMaxSize(),
					contentPadding = PaddingValues(
						horizontal = Ui.Padding.XL,
						vertical = Ui.Padding.L,
					),
					horizontalArrangement = Arrangement.spacedBy(Ui.Padding.L),
				) {
					if (visibleIdeas.isEmpty()) {
						item(span = StaggeredGridItemSpan.FullLine) {
							Box(
								modifier = Modifier
									.fillMaxWidth()
									.padding(vertical = Ui.Padding.XXL),
								contentAlignment = Alignment.Center,
							) {
								Text(
									text = (if (showArchived) Res.string.ideas_list_empty_archived
										else Res.string.ideas_list_empty).get(),
									style = MaterialTheme.typography.headlineSmall,
									color = MaterialTheme.colorScheme.onSurfaceVariant,
								)
							}
						}
					}

					staggeredItems(
						items = visibleIdeas,
						key = { idea -> idea.id.id },
					) { idea ->
						IdeaCard(
							idea = idea,
							activeTags = activeTags,
							onTagClick = toggleTag,
							sharedTransitionScope = sharedTransitionScope,
							animatedVisibilityScope = animatedVisibilityScope,
							modifier = Modifier.padding(bottom = Ui.Padding.L),
							onClick = { component.editIdea(idea.id) },
						)
					}
				}

				MpScrollBarStaggeredGrid(
					modifier = scrollBarOverlay(),
					state = gridState,
				)
			}
		}

		HdFab(
			onClick = { component.showCreate() },
			icon = Icons.Filled.Add,
			contentDescription = Res.string.ideas_create_fab.get(),
			modifier = Modifier
				.align(Alignment.BottomEnd)
				.padding(Ui.Padding.XL)
				.testTag(IDEAS_CREATE_FAB_TAG),
		)
	}
}

@Composable
private fun ArchivedToggle(
	label: String,
	active: Boolean,
	onClick: () -> Unit,
) {
	val background = if (active) {
		MaterialTheme.colorScheme.surfaceContainerHigh
	} else {
		Color.Transparent
	}
	val labelColor = if (active) {
		MaterialTheme.colorScheme.onSurface
	} else {
		MaterialTheme.colorScheme.onSurfaceVariant
	}
	Box(
		modifier = Modifier
			.height(28.dp)
			.background(background, RectangleShape)
			.clickable(onClick = onClick)
			.padding(horizontal = Ui.Padding.L),
		contentAlignment = Alignment.Center,
	) {
		HdMonoLabel(text = label, color = labelColor)
	}
}

@Composable
private fun formatStampDate(instant: Instant): String = remember(instant) {
	instant.toLocalDateTime(TimeZone.currentSystemDefault()).format("dd MMM `yy")
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun IdeaCard(
	idea: StoryIdea,
	activeTags: Set<String>,
	onTagClick: (String) -> Unit,
	sharedTransitionScope: SharedTransitionScope,
	animatedVisibilityScope: AnimatedVisibilityScope,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val date = formatStampDate(idea.created)
	val words = remember(idea.content) { idea.wordCount() }
	val hasHeader = idea.title != null || idea.promoted != null || idea.archived != null

	with(sharedTransitionScope) {
		HdMarkdownCard(
			markdown = idea.content,
			metaStart = date,
			metaEnd = Res.string.ideas_word_count_short.get(words),
			onClick = onClick,
			modifier = modifier,
			surfaceModifier = Modifier
				.sharedElement(
					sharedContentState = rememberSharedContentState(key = "idea-card-${idea.id.id}"),
					animatedVisibilityScope = animatedVisibilityScope,
				)
				.testTag(ideaCardTag(idea.id.id)),
			metaStartModifier = Modifier.sharedElement(
				sharedContentState = rememberSharedContentState(key = "idea-date-${idea.id.id}"),
				animatedVisibilityScope = animatedVisibilityScope,
			),
			markdownModifier = Modifier.sharedElement(
				sharedContentState = rememberSharedContentState(key = "idea-content-${idea.id.id}"),
				animatedVisibilityScope = animatedVisibilityScope,
			),
			tags = idea.tags,
			activeTags = activeTags,
			onTagClick = onTagClick,
			header = if (hasHeader) {
				{
					Column(
						modifier = Modifier
							.fillMaxWidth()
							.padding(horizontal = Ui.Padding.XL, vertical = Ui.Padding.M)
					) {
						idea.title?.let { title ->
							Text(
								text = title,
								style = MaterialTheme.typography.titleMedium,
								color = MaterialTheme.colorScheme.onSurface,
								modifier = Modifier.sharedElement(
									sharedContentState = rememberSharedContentState(
										key = "idea-title-${idea.id.id}",
									),
									animatedVisibilityScope = animatedVisibilityScope,
								),
							)
						}
						IdeaStamps(idea)
					}
				}
			} else {
				null
			},
		)
	}
}

@Composable
private fun IdeaStamps(idea: StoryIdea) {
	if (idea.promoted == null && idea.archived == null) return
	Row(
		modifier = Modifier.padding(top = Ui.Padding.S),
		horizontalArrangement = Arrangement.spacedBy(Ui.Padding.L),
	) {
		idea.promoted?.let {
			HdMonoLabel(
				text = Res.string.ideas_stamp_promoted.get(formatStampDate(it)),
				color = MaterialTheme.colorScheme.primary,
			)
		}
		idea.archived?.let {
			HdMonoLabel(
				text = Res.string.ideas_stamp_archived.get(formatStampDate(it)),
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)
		}
	}
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun IdeaDetail(
	component: StoryIdeas,
	editor: StoryIdeas.Editor,
	draft: StoryIdeas.Draft,
	rootSnackbar: RootSnackbarHostState,
	sharedTransitionScope: SharedTransitionScope,
	animatedVisibilityScope: AnimatedVisibilityScope,
) {
	val scope = rememberCoroutineScope()
	val mainDispatcher = rememberMainDispatcher()
	val strRes = rememberStrRes()

	val existing = (editor as? StoryIdeas.Editor.Edit)?.idea
	val isCreate = existing == null

	// The draft lives in the component so a configuration change can't take unsaved text with it.
	val isEditing = draft.isEditing
	val isDirty = draft.isDirty

	var confirmDelete by rememberSaveable { mutableStateOf(false) }
	var confirmDiscard by rememberSaveable { mutableStateOf(false) }
	var confirmClose by rememberSaveable { mutableStateOf(false) }
	var confirmPromote by rememberSaveable { mutableStateOf(false) }

	val charCount = draft.content.length
	val overLimit = charCount > StoryIdea.MAX_CONTENT_LENGTH
	val canSave = draft.content.isNotBlank() && !overLimit

	suspend fun showError(error: IdeaError) {
		when (error) {
			IdeaError.TOO_LONG -> rootSnackbar.showSnackbar(
				strRes.get(Res.string.ideas_toast_too_long, StoryIdea.MAX_CONTENT_LENGTH)
			)

			IdeaError.EMPTY -> rootSnackbar.showSnackbar(strRes.get(Res.string.ideas_toast_empty))

			IdeaError.TAG_TOO_LONG -> rootSnackbar.showSnackbar(
				strRes.get(Res.string.ideas_toast_tag_too_long, IdeasRepository.MAX_TAG_SIZE)
			)

			IdeaError.NONE -> Unit
		}
	}

	val saveChanges: () -> Unit = {
		scope.launch {
			when (val result = component.saveDraft()) {
				StoryIdeas.SaveResult.Created ->
					rootSnackbar.showSnackbar(strRes.get(Res.string.ideas_toast_created))

				StoryIdeas.SaveResult.Saved ->
					rootSnackbar.showSnackbar(strRes.get(Res.string.ideas_toast_saved))

				is StoryIdeas.SaveResult.Failed -> showError(result.error)
			}
		}
	}

	val cancelEdit: () -> Unit = {
		if (isDirty) confirmDiscard = true else component.discardEdit()
	}

	val requestClose: () -> Unit = {
		if (isDirty) confirmClose = true
		else component.closeEditor()
	}

	val menuItems = if (existing == null) {
		emptySet()
	} else {
		setOf(
			MenuItemDescriptor("idea-delete", Res.string.ideas_delete_button, "") {
				confirmDelete = true
			},
			MenuItemDescriptor(
				"idea-archive",
				if (existing.archived == null) Res.string.ideas_archive_button
				else Res.string.ideas_unarchive_button,
				"",
			) {
				scope.launch {
					val toast = if (existing.archived == null) {
						component.archiveIdea(existing.id)
						Res.string.ideas_toast_archived
					} else {
						component.unarchiveIdea(existing.id)
						Res.string.ideas_toast_unarchived
					}
					withContext(mainDispatcher) { component.closeEditor() }
					rootSnackbar.showSnackbar(strRes.get(toast))
				}
			},
		)
	}

	Box(
		modifier = Modifier
			.fillMaxSize()
			.saveShortcutModifier { if (isEditing) saveChanges() }
			.background(MaterialTheme.colorScheme.surfaceDim),
		contentAlignment = Alignment.TopCenter,
	) {
		with(sharedTransitionScope) {
		Column(
			modifier = Modifier
				.padding(horizontal = Ui.Padding.XL)
				.widthIn(max = DetailMaxWidth)
				.heightIn(max = DetailMaxHeight)
				.fillMaxWidth()
				.then(if (isEditing) Modifier.fillMaxHeight() else Modifier)
				.background(MaterialTheme.colorScheme.surface)
				.border(
					width = Dp.Hairline,
					color = MaterialTheme.colorScheme.outlineVariant,
					shape = RectangleShape,
				)
				.then(
					if (existing != null) {
						Modifier.sharedElement(
							sharedContentState = rememberSharedContentState(
								key = "idea-card-${existing.id.id}",
							),
							animatedVisibilityScope = animatedVisibilityScope,
						)
					} else {
						Modifier
					}
				),
		) {
			CollapseWhileTyping(enabled = isEditing) {
				Column {
					CrumbRow(
						onClose = requestClose,
						menuSlot = {
							if (existing != null) {
								DetailViewDropdownMenu(menuItems = menuItems)
							}
						},
					)

					HorizontalDivider(
						thickness = Dp.Hairline,
						color = MaterialTheme.colorScheme.outlineVariant,
					)
				}
			}

			StampRow(
				isCreate = isCreate,
				isEditing = isEditing,
				idea = existing,
				sharedTransitionScope = sharedTransitionScope,
				animatedVisibilityScope = animatedVisibilityScope,
				onEdit = component::beginEdit,
				onSave = saveChanges,
				onCancel = cancelEdit,
				onPromote = { confirmPromote = true },
				saveEnabled = canSave,
			)

			HorizontalDivider(
				thickness = 2.dp,
				color = MaterialTheme.colorScheme.outline,
			)

			if (isEditing) {
				EditBody(
					titleText = draft.title,
					onTitleChanged = component::updateTitle,
					tags = draft.tags,
					onTagsChanged = component::updateTags,
					onTagDraftChanged = component::updateTagDraft,
					contentText = draft.content,
					onContentChanged = component::updateContent,
					suggestTags = component::suggestTags,
					modifier = Modifier.weight(1f),
				)

				EditStatusFooter(charCount = charCount, overLimit = overLimit)
			} else {
				ViewBody(
					title = draft.savedTitle,
					markdown = draft.savedContent,
					tags = draft.savedTags,
					idea = existing,
					sharedTransitionScope = sharedTransitionScope,
					animatedVisibilityScope = animatedVisibilityScope,
					onEnterEdit = component::beginEdit,
					modifier = Modifier.weight(1f, fill = false),
				)

				ViewFolioFooter(markdown = draft.savedContent, tagCount = draft.savedTags.size)
			}
		}
		}
	}

	if (confirmDiscard || confirmClose) {
		SimpleConfirm(
			title = Res.string.ideas_discard_dialog_title.get(),
			message = Res.string.ideas_discard_dialog_message.get(),
			onDismiss = {
				confirmDiscard = false
				confirmClose = false
			},
		) {
			if (confirmClose || isCreate) {
				component.closeEditor()
			} else {
				component.discardEdit()
			}
			confirmDiscard = false
			confirmClose = false
		}
	}

	if (confirmDelete && existing != null) {
		SimpleConfirm(
			title = Res.string.ideas_delete_dialog_title.get(),
			message = Res.string.ideas_delete_dialog_message.get(),
			onDismiss = { confirmDelete = false },
		) {
			confirmDelete = false
			scope.launch {
				component.deleteIdea(existing.id)
				withContext(mainDispatcher) { component.closeEditor() }
				rootSnackbar.showSnackbar(strRes.get(Res.string.ideas_toast_deleted))
			}
		}
	}

	if (confirmPromote && existing != null) {
		SimpleConfirm(
			title = Res.string.ideas_promote_dialog_title.get(),
			message = Res.string.ideas_promote_dialog_message.get(),
			onDismiss = { confirmPromote = false },
		) {
			confirmPromote = false
			scope.launch {
				val result = component.promoteIdea(existing.id)
				if (isSuccess(result)) {
					withContext(mainDispatcher) { component.closeEditor() }
					rootSnackbar.showSnackbar(
						strRes.get(Res.string.ideas_toast_promoted, result.data.name)
					)
				} else {
					rootSnackbar.showSnackbar(strRes.get(Res.string.ideas_toast_promote_failed))
				}
			}
		}
	}
}

@Composable
private fun CrumbRow(
	onClose: () -> Unit,
	menuSlot: @Composable () -> Unit,
) {
	// Screen masthead â€” see DESIGN_README "Screen masthead" pattern.
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.height(Ui.TOP_BAR_HEIGHT)
			.padding(horizontal = Ui.Padding.XL),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(Ui.Padding.L),
	) {
		HdCrumbBackLink(
			label = Res.string.ideas_view_crumb_root.get(),
			onClick = onClose,
			onClickLabel = Res.string.ideas_view_close_button.get(),
		)
		Spacer(modifier = Modifier.weight(1f))
		menuSlot()
	}
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun StampRow(
	isCreate: Boolean,
	isEditing: Boolean,
	idea: StoryIdea?,
	sharedTransitionScope: SharedTransitionScope,
	animatedVisibilityScope: AnimatedVisibilityScope,
	onEdit: () -> Unit,
	onSave: () -> Unit,
	onCancel: () -> Unit,
	onPromote: () -> Unit,
	saveEnabled: Boolean,
) {
	val sectionTitle = when {
		isCreate -> Res.string.ideas_create_marker.get()
		isEditing -> Res.string.ideas_view_label_editing.get()
		else -> Res.string.ideas_view_header.get()
	}

	HdDetailStampRow(
		stackActionsWhenNarrow = isEditing,
		leading = {
			HdMonoLabel(
				text = "§ II · $sectionTitle",
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)

			if (isEditing) {
				PulsingDot()
			}
		},
		meta = {
			Box(
				modifier = Modifier
					.height(14.dp)
					.width(Dp.Hairline)
					.background(MaterialTheme.colorScheme.outlineVariant),
			)

			val metaText = if (isEditing) {
				Res.string.ideas_view_status_unsaved.get()
			} else {
				idea?.created?.let { formatStampDate(it) }.orEmpty()
			}
			with(sharedTransitionScope) {
				HdMonoLabel(
					text = metaText,
					modifier = if (idea != null) {
						Modifier.sharedElement(
							sharedContentState = rememberSharedContentState(
								key = "idea-date-${idea.id.id}",
							),
							animatedVisibilityScope = animatedVisibilityScope,
						)
					} else {
						Modifier
					},
				)
			}
		},
		actions = {
			if (isEditing) {
				HdHairlineButton(
					label = (if (isCreate) Res.string.ideas_create_button
						else Res.string.ideas_save_button).get(),
					onClick = onSave,
					emphasised = saveEnabled,
					modifier = Modifier.testTag(IDEAS_EDITOR_CONFIRM_TAG),
				)
				HdHairlineButton(
					label = Res.string.ideas_cancel_button.get(),
					onClick = onCancel,
					modifier = Modifier.testTag(IDEAS_EDITOR_CANCEL_TAG),
				)
			} else {
				HdHairlineButton(
					label = Res.string.ideas_promote_button.get(),
					onClick = onPromote,
				)
				HdHairlineButton(
					label = Res.string.ideas_view_action_edit.get(),
					onClick = onEdit,
					modifier = Modifier.testTag(IDEAS_VIEW_EDIT_TAG),
				)
			}
		},
	)
}

@Composable
private fun PulsingDot() {
	val transition = rememberInfiniteTransition(label = "ideaEditingPulse")
	val alpha by transition.animateFloat(
		initialValue = 1f,
		targetValue = 0.35f,
		animationSpec = infiniteRepeatable(
			animation = tween(durationMillis = 800),
			repeatMode = RepeatMode.Reverse,
		),
		label = "ideaEditingPulseAlpha",
	)
	Box(
		modifier = Modifier
			.size(7.dp)
			.alpha(alpha)
			.background(MaterialTheme.colorScheme.primary, RectangleShape),
	)
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalSharedTransitionApi::class)
@Composable
private fun ViewBody(
	title: String?,
	markdown: String,
	tags: Set<String>,
	idea: StoryIdea?,
	sharedTransitionScope: SharedTransitionScope,
	animatedVisibilityScope: AnimatedVisibilityScope,
	onEnterEdit: () -> Unit,
	modifier: Modifier = Modifier,
) = with(sharedTransitionScope) {
	val scrollState = rememberScrollState()
	Box(modifier = modifier) {
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.verticalScroll(scrollState)
				.padding(horizontal = Ui.Padding.XL, vertical = Ui.Padding.XL),
		) {
			title?.let {
				Text(
					text = it,
					style = MaterialTheme.typography.headlineSmall,
					color = MaterialTheme.colorScheme.onSurface,
					modifier = Modifier
						.padding(bottom = Ui.Padding.M)
						.then(
							if (idea != null) {
								Modifier.sharedElement(
									sharedContentState = rememberSharedContentState(
										key = "idea-title-${idea.id.id}",
									),
									animatedVisibilityScope = animatedVisibilityScope,
								)
							} else {
								Modifier
							}
						),
				)
			}

			idea?.let { IdeaStamps(it) }

			if (tags.isNotEmpty()) {
				FlowRow(
					modifier = Modifier
						.fillMaxWidth()
						.padding(top = Ui.Padding.M, bottom = Ui.Padding.L),
					horizontalArrangement = Arrangement.spacedBy(6.dp),
					verticalArrangement = Arrangement.spacedBy(6.dp),
				) {
					tags.sorted().forEach { tag ->
						HdTagChip(
							label = tag,
							active = true,
							onClick = {},
						)
					}
				}

				HorizontalDivider(
					thickness = Dp.Hairline,
					color = MaterialTheme.colorScheme.outlineVariant,
					modifier = Modifier.padding(bottom = Ui.Padding.L),
				)
			}

			MarkdownView(
				markdown = markdown,
				modifier = Modifier
					.fillMaxWidth()
					.then(
						if (idea != null) {
							Modifier.sharedElement(
								sharedContentState = rememberSharedContentState(
									key = "idea-content-${idea.id.id}",
								),
								animatedVisibilityScope = animatedVisibilityScope,
							)
						} else {
							Modifier
						}
					)
					.clickable(onClick = onEnterEdit),
			)
		}

		MpScrollBarColumn(
			modifier = scrollBarOverlay(),
			state = scrollState,
		)
	}
}

@Composable
private fun EditBody(
	titleText: String,
	onTitleChanged: (String) -> Unit,
	tags: List<String>,
	onTagsChanged: (List<String>) -> Unit,
	onTagDraftChanged: (String) -> Unit,
	contentText: String,
	onContentChanged: (String) -> Unit,
	suggestTags: (prefix: String) -> List<String>,
	modifier: Modifier = Modifier,
) {
	Column(modifier = modifier.fillMaxWidth()) {
		var titleFocused by remember { mutableStateOf(false) }
		CollapseWhileTyping(keepVisible = titleFocused) {
			HdHairlineField(
				label = Res.string.ideas_title_label.get(),
				value = titleText,
				onValueChange = onTitleChanged,
				placeholder = Res.string.ideas_title_placeholder.get(),
				onFocusChanged = { titleFocused = it },
				testTag = IDEAS_EDITOR_TITLE_TAG,
				modifier = Modifier
					.fillMaxWidth()
					.padding(horizontal = Ui.Padding.XL)
					.padding(top = Ui.Padding.L),
			)
		}

		HdHairlineTagField(
			label = Res.string.ideas_tags_label.get(),
			tags = tags,
			onTagsChange = onTagsChanged,
			onDraftChange = onTagDraftChanged,
			hint = Res.string.ideas_tags_hint.get(),
			placeholder = Res.string.ideas_tags_placeholder.get(),
			suggestTags = suggestTags,
			modifier = Modifier.padding(
				horizontal = Ui.Padding.XL,
				vertical = Ui.Padding.L,
			),
		)

		CollapseWhileTyping {
			HorizontalDivider(
				thickness = 2.dp,
				color = MaterialTheme.colorScheme.outline,
			)
		}

		MarkdownEditField(
			initialMarkdown = contentText,
			onMarkdownChanged = onContentChanged,
			contentPadding = PaddingValues(Ui.Padding.XL),
			testTag = IDEAS_EDITOR_BODY_TAG,
			modifier = Modifier
				.fillMaxWidth()
				.weight(1f),
		)
	}
}

@Composable
private fun ViewFolioFooter(markdown: String, tagCount: Int) {
	val words = remember(markdown) {
		markdown.trim().split(Regex("\\s+")).count { it.isNotBlank() }
	}
	HorizontalDivider(
		thickness = Dp.Hairline,
		color = MaterialTheme.colorScheme.outlineVariant,
	)
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = Ui.Padding.XL, vertical = Ui.Padding.M),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(Ui.Padding.L),
	) {
		HdMonoLabel(text = Res.string.ideas_word_count_short.get(words))
		Spacer(modifier = Modifier.weight(1f))
		if (tagCount > 0) {
			HdMonoLabel(text = Res.string.ideas_tag_count_short.get(tagCount))
		}
	}
}

@Composable
private fun EditStatusFooter(charCount: Int, overLimit: Boolean) {
	HorizontalDivider(
		thickness = Dp.Hairline,
		color = MaterialTheme.colorScheme.outlineVariant,
	)
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.background(MaterialTheme.colorScheme.surfaceContainerLow)
			.padding(horizontal = Ui.Padding.XL, vertical = Ui.Padding.M),
		verticalAlignment = Alignment.CenterVertically,
	) {
		HdMonoLabel(
			text = Res.string.ideas_editor_counter.get(
				charCount,
				StoryIdea.MAX_CONTENT_LENGTH,
			),
			color = if (overLimit) {
				MaterialTheme.colorScheme.error
			} else {
				MaterialTheme.colorScheme.onSurfaceVariant
			},
		)
	}
}
