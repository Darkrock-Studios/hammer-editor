package com.darkrockstudios.apps.hammer.common.projectselection.storyideas

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
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
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.darkrockstudios.apps.hammer.common.compose.LocalScreenCharacteristic
import com.darkrockstudios.apps.hammer.common.compose.RootSnackbarHostState
import com.darkrockstudios.apps.hammer.common.compose.SimpleConfirm
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdActiveFiltersStrip
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdCollapsingStrip
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdFab
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdFolioDivider
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineButton
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineField
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineTagField
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMarkdownCard
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMonoLabel
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdSearchField
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdSearchRow
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdSectionHeader
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdSortMenu
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdSortOption
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdTagFilterBar
import com.darkrockstudios.apps.hammer.common.compose.firstNonBlankLine
import com.darkrockstudios.apps.hammer.common.compose.markdowneditor.MarkdownEditField
import com.darkrockstudios.apps.hammer.common.compose.rememberMainDispatcher
import com.darkrockstudios.apps.hammer.common.compose.rememberStrRes
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.ideasrepository.IdeaError
import com.darkrockstudios.apps.hammer.common.data.ideasrepository.IdeasRepository
import com.darkrockstudios.apps.hammer.common.data.ideasrepository.idea.StoryIdea
import com.darkrockstudios.apps.hammer.common.data.tagindex.TagCount
import com.darkrockstudios.apps.hammer.common.util.format
import com.darkrockstudios.apps.hammer.ideas_archive_button
import com.darkrockstudios.apps.hammer.ideas_cancel_button
import com.darkrockstudios.apps.hammer.ideas_create_button
import com.darkrockstudios.apps.hammer.ideas_create_fab
import com.darkrockstudios.apps.hammer.ideas_create_header
import com.darkrockstudios.apps.hammer.ideas_create_marker
import com.darkrockstudios.apps.hammer.ideas_delete_button
import com.darkrockstudios.apps.hammer.ideas_delete_dialog_message
import com.darkrockstudios.apps.hammer.ideas_delete_dialog_title
import com.darkrockstudios.apps.hammer.ideas_discard_dialog_message
import com.darkrockstudios.apps.hammer.ideas_discard_dialog_title
import com.darkrockstudios.apps.hammer.ideas_edit_header
import com.darkrockstudios.apps.hammer.ideas_edit_marker
import com.darkrockstudios.apps.hammer.ideas_editor_counter
import com.darkrockstudios.apps.hammer.ideas_filter_all
import com.darkrockstudios.apps.hammer.ideas_filter_archived
import com.darkrockstudios.apps.hammer.ideas_filter_clear_all
import com.darkrockstudios.apps.hammer.ideas_filter_filtered
import com.darkrockstudios.apps.hammer.ideas_header
import com.darkrockstudios.apps.hammer.ideas_header_meta
import com.darkrockstudios.apps.hammer.ideas_list_empty
import com.darkrockstudios.apps.hammer.ideas_list_empty_archived
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
import com.darkrockstudios.apps.hammer.ideas_toast_created
import com.darkrockstudios.apps.hammer.ideas_toast_deleted
import com.darkrockstudios.apps.hammer.ideas_toast_empty
import com.darkrockstudios.apps.hammer.ideas_toast_saved
import com.darkrockstudios.apps.hammer.ideas_toast_tag_too_long
import com.darkrockstudios.apps.hammer.ideas_toast_too_long
import com.darkrockstudios.apps.hammer.ideas_unarchive_button
import com.darkrockstudios.apps.hammer.ideas_word_count_short
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant
import androidx.compose.foundation.lazy.staggeredgrid.items as staggeredItems

const val IDEAS_CREATE_FAB_TAG = "ideas-create-fab"
const val IDEAS_EDITOR_BODY_TAG = "ideas-editor-body"
const val IDEAS_EDITOR_CONFIRM_TAG = "ideas-editor-confirm"
const val IDEAS_EDITOR_CANCEL_TAG = "ideas-editor-cancel"
fun ideaCardTag(id: String) = "idea-card-$id"

private enum class IdeasSortMode(
	override val labelRes: StringResource,
	override val glyphRes: StringResource,
) : HdSortOption {
	DateDesc(Res.string.ideas_sort_newest, Res.string.ideas_sort_glyph_date_desc),
	DateAsc(Res.string.ideas_sort_oldest, Res.string.ideas_sort_glyph_date_asc),
	TitleAsc(Res.string.ideas_sort_title_az, Res.string.ideas_sort_glyph_title_az),
}

private fun StoryIdea.wordCount(): Int =
	content.split(Regex("\\s+")).count { it.isNotBlank() }

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

	AnimatedContent(
		targetState = state.editor,
		modifier = modifier.fillMaxSize(),
		transitionSpec = { fadeIn() togetherWith fadeOut() },
		contentKey = { it != null },
		label = "StoryIdeasEditorSwap",
	) { editor ->
		if (editor != null) {
			IdeaEditor(
				component = component,
				editor = editor,
				rootSnackbar = rootSnackbar,
			)
		} else {
			IdeasBrowse(
				component = component,
				ideas = state.ideas,
			)
		}
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IdeasBrowse(
	component: StoryIdeas,
	ideas: List<StoryIdea>,
) {
	val screen = LocalScreenCharacteristic.current
	val isWide = screen.isWide
	val isExpanded = screen.windowWidthClass == WindowWidthSizeClass.Expanded

	var searchQuery by rememberSaveable { mutableStateOf("") }
	var sortMode by remember { mutableStateOf(IdeasSortMode.DateDesc) }
	var activeTags by remember { mutableStateOf<Set<String>>(emptySet()) }
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
				scopedIdeas.filter { idea ->
					idea.content.contains(query, ignoreCase = true) ||
						idea.title?.contains(query, ignoreCase = true) == true
				}
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
						placeholder = stringResource(Res.string.ideas_search_placeholder),
						clearContentDescription = stringResource(Res.string.ideas_search_clear),
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
							title = stringResource(Res.string.ideas_header),
							modifier = Modifier.weight(1f),
							trailing = {
								if (isWide) {
									HdMonoLabel(
										text = stringResource(
											Res.string.ideas_header_meta,
											ideas.size,
											rankedTags.size,
										),
									)
								}
							},
						)
						if (!isWide) {
							IconButton(onClick = { showSearchBar = true }) {
								Icon(
									imageVector = Icons.Default.Search,
									contentDescription = Res.string.ideas_search_button.get(),
									tint = MaterialTheme.colorScheme.onSurface,
								)
							}
						}
					}
				}
			}

			HdFolioDivider()

			HdCollapsingStrip(scrollBehavior = scrollBehavior) {
				val searchField: @Composable () -> Unit = {
					HdSearchField(
						value = searchQuery,
						onValueChange = { searchQuery = it },
						modifier = Modifier
							.then(if (isExpanded) Modifier.width(280.dp) else Modifier.fillMaxWidth()),
						placeholder = stringResource(Res.string.ideas_search_placeholder),
						onClear = { searchQuery = "" },
						clearContentDescription = stringResource(Res.string.ideas_search_clear),
					)
				}

				if (isWide && !isExpanded) {
					Row(
						modifier = Modifier
							.fillMaxWidth()
							.padding(horizontal = Ui.Padding.XL, vertical = Ui.Padding.M),
						verticalAlignment = Alignment.CenterVertically,
					) {
						searchField()
					}
				}

				HdTagFilterBar(
					tags = rankedTags,
					allLabel = "${stringResource(Res.string.ideas_filter_all)} · ${scopedIdeas.size}",
					activeTags = activeTags,
					onToggle = toggleTag,
					onClear = clearTags,
					leading = if (isExpanded) {
						{ searchField() }
					} else {
						null
					},
					trailing = {
						val archivedCount = ideas.count { it.archived != null }
						ArchivedToggle(
							label = "${stringResource(Res.string.ideas_filter_archived)} · $archivedCount",
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
						filteredLabel = stringResource(
							Res.string.ideas_filter_filtered,
							visibleIdeas.size,
							scopedIdeas.size,
						),
						clearAllLabel = stringResource(Res.string.ideas_filter_clear_all),
						onToggle = toggleTag,
						onClear = clearTags,
					)
				}

				HorizontalDivider(
					thickness = Dp.Hairline,
					color = MaterialTheme.colorScheme.outlineVariant,
				)
			}

			LazyVerticalStaggeredGrid(
				columns = StaggeredGridCells.Adaptive(400.dp),
				modifier = Modifier.fillMaxSize(),
				contentPadding = PaddingValues(
					horizontal = Ui.Padding.XL,
					vertical = Ui.Padding.L,
				),
				horizontalArrangement = Arrangement.spacedBy(Ui.Padding.L),
			) {
				if (visibleIdeas.isEmpty()) {
					item {
						Box(
							modifier = Modifier
								.fillMaxWidth()
								.padding(vertical = Ui.Padding.XXL),
							contentAlignment = Alignment.Center,
						) {
							Text(
								text = stringResource(
									if (showArchived) Res.string.ideas_list_empty_archived
									else Res.string.ideas_list_empty
								),
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
						modifier = Modifier.padding(bottom = Ui.Padding.L),
						onClick = { component.editIdea(idea.id) },
					)
				}
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

@Composable
private fun IdeaCard(
	idea: StoryIdea,
	activeTags: Set<String>,
	onTagClick: (String) -> Unit,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val date = formatStampDate(idea.created)
	val words = remember(idea.content) { idea.wordCount() }
	val hasHeader = idea.title != null || idea.promoted != null || idea.archived != null

	HdMarkdownCard(
		markdown = idea.content,
		metaStart = date,
		metaEnd = stringResource(Res.string.ideas_word_count_short, words),
		onClick = onClick,
		modifier = modifier,
		surfaceModifier = Modifier.testTag(ideaCardTag(idea.id.id)),
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

@Composable
private fun IdeaStamps(idea: StoryIdea) {
	if (idea.promoted == null && idea.archived == null) return
	Row(
		modifier = Modifier.padding(top = Ui.Padding.S),
		horizontalArrangement = Arrangement.spacedBy(Ui.Padding.L),
	) {
		idea.promoted?.let {
			HdMonoLabel(
				text = stringResource(Res.string.ideas_stamp_promoted, formatStampDate(it)),
				color = MaterialTheme.colorScheme.primary,
			)
		}
		idea.archived?.let {
			HdMonoLabel(
				text = stringResource(Res.string.ideas_stamp_archived, formatStampDate(it)),
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)
		}
	}
}

@Composable
private fun IdeaEditor(
	component: StoryIdeas,
	editor: StoryIdeas.Editor,
	rootSnackbar: RootSnackbarHostState,
) {
	val scope = androidx.compose.runtime.rememberCoroutineScope()
	val mainDispatcher = rememberMainDispatcher()
	val strRes = rememberStrRes()

	val existing = (editor as? StoryIdeas.Editor.Edit)?.idea

	var titleText by remember(editor) { mutableStateOf(existing?.title.orEmpty()) }
	var contentText by remember(editor) { mutableStateOf(existing?.content.orEmpty()) }
	var tags by remember(editor) { mutableStateOf(existing?.tags?.toList().orEmpty()) }
	var confirmDelete by remember { mutableStateOf(false) }
	var confirmDiscard by remember { mutableStateOf(false) }

	val isDirty = titleText != existing?.title.orEmpty() ||
		contentText != existing?.content.orEmpty() ||
		tags.toSet() != (existing?.tags ?: emptySet<String>())

	val charCount = contentText.length
	val overLimit = charCount > StoryIdea.MAX_CONTENT_LENGTH
	val canSave = contentText.isNotBlank() && !overLimit

	val requestClose: () -> Unit = {
		if (isDirty) confirmDiscard = true
		else component.closeEditor()
	}

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

	Column(
		modifier = Modifier
			.fillMaxSize()
			.widthIn(max = TextEditorDefaults.MAX_WIDTH * 1.25f),
	) {
		CollapseWhileTyping {
			Column(modifier = Modifier.fillMaxWidth()) {
				HdSectionHeader(
					marker = stringResource(
						if (existing == null) Res.string.ideas_create_marker
						else Res.string.ideas_edit_marker
					),
					title = stringResource(
						if (existing == null) Res.string.ideas_create_header
						else Res.string.ideas_edit_header
					),
					trailing = {
						existing?.let { IdeaStamps(it) }
					},
					modifier = Modifier
						.fillMaxWidth()
						.height(Ui.TOP_BAR_HEIGHT)
						.padding(horizontal = Ui.Padding.XL),
				)

				HdFolioDivider()
			}
		}

		var titleFocused by remember { mutableStateOf(false) }
		CollapseWhileTyping(keepVisible = titleFocused) {
			HdHairlineField(
				label = stringResource(Res.string.ideas_title_label),
				value = titleText,
				onValueChange = { titleText = it },
				placeholder = stringResource(Res.string.ideas_title_placeholder),
				onFocusChanged = { titleFocused = it },
				modifier = Modifier
					.fillMaxWidth()
					.padding(horizontal = Ui.Padding.XL, vertical = Ui.Padding.L),
			)
		}

		HdHairlineTagField(
			label = stringResource(Res.string.ideas_tags_label),
			tags = tags,
			onTagsChange = { tags = it },
			hint = stringResource(Res.string.ideas_tags_hint),
			placeholder = stringResource(Res.string.ideas_tags_placeholder),
			suggestTags = component::suggestTags,
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = Ui.Padding.XL)
				.padding(bottom = Ui.Padding.L),
		)

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
			key(editor) {
				MarkdownEditField(
					initialMarkdown = contentText,
					onMarkdownChanged = { contentText = it },
					contentPadding = PaddingValues(Ui.Padding.XL),
					testTag = IDEAS_EDITOR_BODY_TAG,
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
			HdMonoLabel(
				text = stringResource(
					Res.string.ideas_editor_counter,
					charCount,
					StoryIdea.MAX_CONTENT_LENGTH,
				),
				color = if (overLimit) {
					MaterialTheme.colorScheme.error
				} else {
					MaterialTheme.colorScheme.onSurfaceVariant
				},
			)

			Spacer(modifier = Modifier.weight(1f))

			if (existing != null) {
				HdHairlineButton(
					label = stringResource(Res.string.ideas_delete_button),
					onClick = { confirmDelete = true },
				)
				HdHairlineButton(
					label = stringResource(
						if (existing.archived == null) Res.string.ideas_archive_button
						else Res.string.ideas_unarchive_button
					),
					onClick = {
						scope.launch {
							if (existing.archived == null) {
								component.archiveIdea(existing.id)
							} else {
								component.unarchiveIdea(existing.id)
							}
							withContext(mainDispatcher) { component.closeEditor() }
						}
					},
				)
			}

			HdHairlineButton(
				label = stringResource(Res.string.ideas_cancel_button),
				onClick = requestClose,
				modifier = Modifier.testTag(IDEAS_EDITOR_CANCEL_TAG),
			)
			HdHairlineButton(
				label = stringResource(
					if (existing == null) Res.string.ideas_create_button
					else Res.string.ideas_save_button
				),
				emphasised = canSave,
				modifier = Modifier.testTag(IDEAS_EDITOR_CONFIRM_TAG),
				onClick = {
					scope.launch {
						val title = titleText.trim().ifEmpty { null }
						val error = if (existing == null) {
							component.createIdea(title, contentText, tags.toSet())
						} else {
							component.saveIdea(existing.id, title, contentText, tags.toSet())
						}
						if (error == IdeaError.NONE) {
							withContext(mainDispatcher) { component.closeEditor() }
							rootSnackbar.showSnackbar(
								strRes.get(
									if (existing == null) Res.string.ideas_toast_created
									else Res.string.ideas_toast_saved
								)
							)
						} else {
							showError(error)
						}
					}
				},
			)
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

	if (confirmDiscard) {
		SimpleConfirm(
			title = Res.string.ideas_discard_dialog_title.get(),
			message = Res.string.ideas_discard_dialog_message.get(),
			onDismiss = { confirmDiscard = false },
		) {
			confirmDiscard = false
			component.closeEditor()
		}
	}
}
