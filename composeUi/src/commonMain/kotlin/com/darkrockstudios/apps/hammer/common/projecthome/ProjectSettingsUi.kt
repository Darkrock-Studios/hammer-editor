package com.darkrockstudios.apps.hammer.common.projecthome

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdEmptyState
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMetaText
import androidx.compose.material.icons.outlined.Extension
import com.darkrockstudios.apps.hammer.project_settings_plugins_empty_body
import com.darkrockstudios.apps.hammer.project_settings_plugins_empty_body_here
import com.darkrockstudios.apps.hammer.plugins.wasmhost.RuntimePlugins
import org.koin.mp.KoinPlatform.getKoin
import com.darkrockstudios.apps.hammer.project_settings_plugins_empty_title
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdQuietButton
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdSettingGroup
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdSettingRow
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdTabPill
import com.darkrockstudios.apps.hammer.project_settings_dictionary_title
import com.darkrockstudios.apps.hammer.project_settings_enter_to_add_word
import com.darkrockstudios.apps.hammer.project_settings_tab_plugins
import com.darkrockstudios.apps.hammer.project_settings_tab_project
import com.darkrockstudios.apps.hammer.project_settings_tab_timelines
import com.darkrockstudios.apps.hammer.project_settings_tab_writing
import com.darkrockstudios.apps.hammer.common.compose.designsystem.hdColumnMaxWidth
import com.darkrockstudios.apps.hammer.common.compose.designsystem.hdSectionGap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.darkrockstudios.apps.hammer.common.compose.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.components.projecthome.ProjectSettings
import com.darkrockstudios.apps.hammer.common.components.spellchecksettings.SpellCheckSettings
import com.darkrockstudios.apps.hammer.common.compose.LocalScreenCharacteristic
import com.darkrockstudios.apps.hammer.common.compose.MpScrollBarColumn
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdCrumbBackLink
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdFolioDivider
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineDropdown
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineSection
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineToggleRow
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineWordListField
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMonoLabel
import com.darkrockstudios.apps.hammer.common.compose.plugin.PluginUiRegistry
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.compose.scrollBarOverlay
import com.darkrockstudios.apps.hammer.common.compose.theme.LocalHammerColors
import com.darkrockstudios.apps.hammer.common.projectselection.settings.ManagePluginsButton
import com.darkrockstudios.apps.hammer.common.projectselection.settings.SpellCheckSettingsContent
import com.darkrockstudios.apps.hammer.common.spellcheck.displayName
import com.darkrockstudios.apps.hammer.common.spellcheck.isSpellCheckAllowedForProject
import com.darkrockstudios.apps.hammer.common.spellcheck.normalizeDictionaryWord
import com.darkrockstudios.apps.hammer.common.util.Locale
import com.darkrockstudios.apps.hammer.operations.plugin.ClientPlugin
import com.darkrockstudios.apps.hammer.project_settings_autosaved
import com.darkrockstudios.apps.hammer.project_settings_breadcrumb_home
import com.darkrockstudios.apps.hammer.project_settings_breadcrumb_root
import com.darkrockstudios.apps.hammer.project_settings_dictionary_hint
import com.darkrockstudios.apps.hammer.project_settings_dictionary_label
import com.darkrockstudios.apps.hammer.project_settings_dictionary_placeholder
import com.darkrockstudios.apps.hammer.project_settings_folio_caption
import com.darkrockstudios.apps.hammer.project_settings_folio_section_count
import com.darkrockstudios.apps.hammer.project_settings_hero_by
import com.darkrockstudios.apps.hammer.project_settings_hero_marker
import com.darkrockstudios.apps.hammer.project_settings_hero_no_author
import com.darkrockstudios.apps.hammer.project_settings_plugins_caption
import com.darkrockstudios.apps.hammer.project_settings_plugins_pick_none
import com.darkrockstudios.apps.hammer.project_settings_spellcheck_encyclopedia_enable
import com.darkrockstudios.apps.hammer.project_settings_spellcheck_encyclopedia_enable_hint
import com.darkrockstudios.apps.hammer.project_settings_spellcheck_mismatch_caption
import com.darkrockstudios.apps.hammer.project_settings_spellcheck_section_title
import androidx.compose.ui.platform.testTag
import com.darkrockstudios.apps.hammer.common.projectselection.sharedByLabel
import com.darkrockstudios.apps.hammer.project_settings_sync_section_title
import com.darkrockstudios.apps.hammer.project_settings_sync_shared_off
import com.darkrockstudios.apps.hammer.project_settings_sync_shared_syncing
import com.darkrockstudios.apps.hammer.project_settings_sync_own_hint
import com.darkrockstudios.apps.hammer.project_settings_sync_own_off
import com.darkrockstudios.apps.hammer.project_settings_sync_own_on
import org.koin.compose.koinInject

private val MaxColumnWidth = 880.dp

const val ProjectSyncOwnTestTag = "project-settings-sync-own"
const val ProjectSyncSharedTestTag = "project-settings-sync-shared"

/** The tabs of project Settings. */
enum class ProjectSettingsTab { Project, Writing, Timelines, Plugins }

/**
 * Project settings as four tabs of setting rows. The project's name shows once, as the title of
 * its details group; the masthead names the screen.
 */
@Composable
fun ProjectSettingsUi(
	modifier: Modifier,
	component: ProjectSettings,
	onClose: () -> Unit,
	initialTab: ProjectSettingsTab = ProjectSettingsTab.Project,
) {
	val state by component.projectInfoState.subscribeAsState()
	val plugins = koinInject<PluginUiRegistry>().plugins()
	val disabledPlugins by component.disabledPlugins.subscribeAsState()
	val syncState by component.syncState.subscribeAsState()
	val screen = LocalScreenCharacteristic.current
	val isCompact = screen.windowWidthClass == WindowWidthSizeClass.Compact
	val horizontal: Dp = if (isCompact) Ui.Padding.XL else 56.dp
	val tabs = ProjectSettingsTab.entries
	var tab by rememberSaveable { mutableStateOf(initialTab) }

	Column(
		modifier = modifier
			.fillMaxSize()
			.background(MaterialTheme.colorScheme.surface),
	) {
		Masthead(horizontal = horizontal, onClose = onClose)
		HdFolioDivider()

		Box(modifier = Modifier.fillMaxWidth().padding(horizontal = horizontal)) {
			Row(
				modifier = Modifier
					.widthIn(max = hdColumnMaxWidth(MaxColumnWidth))
					.fillMaxWidth()
					.align(Alignment.TopCenter),
			) {
				tabs.forEach { option ->
					HdTabPill(
						label = tabLabel(option),
						active = option == tab,
						onClick = { tab = option },
						modifier = Modifier.weight(1f),
					)
				}
			}
		}

		key(tab) {
			val scrollState = rememberScrollState()
			Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
				Column(
					modifier = Modifier
						.fillMaxSize()
						.verticalScroll(scrollState)
						.padding(horizontal = horizontal, vertical = if (isCompact) Ui.Padding.XL else 32.dp),
				) {
					Column(
						modifier = Modifier
							.widthIn(max = hdColumnMaxWidth(MaxColumnWidth))
							.fillMaxWidth()
							.align(Alignment.CenterHorizontally),
						verticalArrangement = Arrangement.spacedBy(56.dp),
					) {
						if (state.isLoaded) {
							when (tab) {
								ProjectSettingsTab.Project -> {
									ProjectDetailsGroup(component, component.projectName) {
										if (syncState.serverConfigured) ProjectSyncRow(syncState)
									}
									CoverImageSection(
										coverImageId = state.data.coverImageId,
										coverImage = state.coverImage,
										pickPending = state.pendingCoverPick,
										onPick = component::pickCoverImage,
										onClear = component::clearCoverImage,
									)
									ProjectTagsGroup(
										tags = state.data.tags,
										onChange = component::setTags,
										suggest = component::suggestProjectTags,
									)
									CustomThemeGroup(theme = state.data.theme, onChange = component::setTheme)
								}

								ProjectSettingsTab.Writing -> {
									WordCountGoalGroup(goal = state.data.wordCountGoal, onChange = component::setWordCountGoal)
									HdSettingGroup(title = Res.string.project_settings_spellcheck_section_title.get()) {
										SpellCheckSettingsContent(component.spellCheckSettings, showEncyclopediaToggle = false)
										ProjectDictionaryToggle(enabled = state.data.encyclopediaDictionary, component = component)
										SpellCheckMismatchCaption(
											projectLanguageTag = state.data.language,
											spellCheckSettings = component.spellCheckSettings,
										)
									}
									HdSettingGroup(title = Res.string.project_settings_dictionary_title.get()) {
										ProjectDictionaryWords(words = state.data.dictionaryWords, component = component)
									}
								}

								ProjectSettingsTab.Timelines -> TimelineSettingsUi(component = component.timelineSettings)
								ProjectSettingsTab.Plugins -> ProjectPlugins(
									plugins = plugins,
									disabled = disabledPlugins,
									language = state.data.language,
									component = component,
								)
							}
						}
						Spacer(Modifier.height(8.dp))
					}
				}
				MpScrollBarColumn(modifier = scrollBarOverlay(), state = scrollState)
			}
		}
	}
}

@Composable
private fun tabLabel(tab: ProjectSettingsTab): String = when (tab) {
	ProjectSettingsTab.Project -> Res.string.project_settings_tab_project.get()
	ProjectSettingsTab.Writing -> Res.string.project_settings_tab_writing.get()
	ProjectSettingsTab.Timelines -> Res.string.project_settings_tab_timelines.get()
	ProjectSettingsTab.Plugins -> Res.string.project_settings_tab_plugins.get()
}

/** Per-project override for the global "learn encyclopedia names" feature. */
@Composable
private fun ProjectDictionaryToggle(
	enabled: Boolean,
	component: ProjectSettings,
) {
	val spellCheckState by component.spellCheckSettings.state.subscribeAsState()
	if (!spellCheckState.spellCheckingEncyclopediaEnabled) return

	HdHairlineToggleRow(
		checked = enabled,
		label = Res.string.project_settings_spellcheck_encyclopedia_enable.get(),
		hint = Res.string.project_settings_spellcheck_encyclopedia_enable_hint.get(),
		enabled = spellCheckState.spellCheckingEnabled,
		onCheckedChange = { component.setEncyclopediaDictionaryEnabled(it) },
	)
}

/** The user-added words this project's spell checker accepts, synced with the project. */
@Composable
private fun ProjectDictionaryWords(
	words: Set<String>,
	component: ProjectSettings,
) {
	val sorted = remember(words) { words.sortedBy { it.lowercase() } }
	Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
		HdHairlineWordListField(
			label = "",
			words = sorted,
			onAdd = component::addDictionaryWord,
			onRemove = component::removeDictionaryWord,
			parseInput = ::normalizeDictionaryWord,
			placeholder = Res.string.project_settings_dictionary_placeholder.get(),
			testTag = "project-settings-dictionary",
		)
		Text(
			text = Res.string.project_settings_enter_to_add_word.get(),
			style = MaterialTheme.typography.bodySmall,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
	}
}

/**
 * Manage plugins, a dropdown for each kind with several plugins, those for the project's [language]
 * first, then a toggle for each other active plugin, turning it off for this project alone.
 */
@Composable
private fun ProjectPlugins(
	plugins: List<ClientPlugin>,
	disabled: Set<String>,
	language: String?,
	component: ProjectSettings,
) {
	val choices = remember(plugins, language) { pluginChoices(plugins, language) }
	val sorted = remember(plugins, choices) {
		val chosen = choices.flatMap { choice -> choice.plugins.map { it.id } }.toSet()
		plugins.filter { it.id !in chosen }.sortedBy { (it.name ?: it.id).lowercase() }
	}
	if (plugins.isEmpty()) {
		// The button is missing where plugins can't be installed, so the text only points to it when it's there.
		val canInstall = remember { getKoin().getOrNull<RuntimePlugins>() != null }
		HdEmptyState(
			icon = Icons.Outlined.Extension,
			title = Res.string.project_settings_plugins_empty_title.get(),
			body = if (canInstall) {
				Res.string.project_settings_plugins_empty_body_here.get()
			} else {
				Res.string.project_settings_plugins_empty_body.get()
			},
			action = { ManagePluginsButton() },
		)
		return
	}
	// Untitled: the tab already says "Plugins".
	HdSettingGroup(title = null) {
		Text(
			text = Res.string.project_settings_plugins_caption.get(),
			style = MaterialTheme.typography.bodySmall,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
		ManagePluginsButton()
		choices.forEach { choice ->
			HdHairlineDropdown(
				options = listOf<PluginPick>(PluginPick.None) + choice.plugins.map(PluginPick::One),
				selected = choice.picked(disabled),
				onSelect = { component.setPluginsEnabled(choice.changesFor(it)) },
				label = { pick ->
					when (pick) {
						PluginPick.None -> Res.string.project_settings_plugins_pick_none.get()
						is PluginPick.One -> pick.plugin.name ?: pick.plugin.id
					}
				},
				title = choice.label,
			)
		}
		sorted.forEach { plugin ->
			HdHairlineToggleRow(
				checked = plugin.id !in disabled,
				label = plugin.name ?: plugin.id,
				onCheckedChange = { component.setPluginEnabled(plugin.id, it) },
			)
		}
	}
}

/**
 * Whether this device syncs the project, read-only: an own project's switch is in its menu on the
 * project list, where it is closed; a shared project says whose it is and how it stops syncing.
 */
@Composable
internal fun ProjectSyncRow(state: ProjectSettings.SyncState) {
	val owner = state.owner
	if (owner != null) {
		HdSettingRow(
			title = sharedByLabel(owner.name),
			description = if (state.syncEnabled) {
				Res.string.project_settings_sync_shared_syncing.get()
			} else {
				Res.string.project_settings_sync_shared_off.get()
			},
			modifier = Modifier.testTag(ProjectSyncSharedTestTag),
		) {}
	} else {
		HdSettingRow(
			title = if (state.syncEnabled) {
				Res.string.project_settings_sync_own_on.get()
			} else {
				Res.string.project_settings_sync_own_off.get()
			},
			description = Res.string.project_settings_sync_own_hint.get(),
			modifier = Modifier.testTag(ProjectSyncOwnTestTag),
		) {}
	}
}

/** Explains why spell check isn't running when the project's language gates it off. */
@Composable
private fun SpellCheckMismatchCaption(
	projectLanguageTag: String?,
	spellCheckSettings: SpellCheckSettings,
) {
	val spellCheckState by spellCheckSettings.state.subscribeAsState()
	if (!spellCheckState.spellCheckingEnabled) return
	if (projectLanguageTag == null) return
	if (isSpellCheckAllowedForProject(projectLanguageTag, spellCheckState.spellCheckingLanguage)) return

	Text(
		text = Res.string.project_settings_spellcheck_mismatch_caption.get(
			Locale.forLanguageTag(projectLanguageTag).displayName(),
			spellCheckState.spellCheckingLanguage.displayName(),
		),
		style = MaterialTheme.typography.bodySmall,
		color = MaterialTheme.colorScheme.onSurfaceVariant,
	)
}

@Composable
private fun Masthead(horizontal: Dp, onClose: () -> Unit) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.height(Ui.TOP_BAR_HEIGHT)
			.padding(horizontal = horizontal),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(Ui.Padding.L),
	) {
		HdQuietButton(
			onClick = onClose,
			icon = Icons.AutoMirrored.Filled.ArrowBack,
			label = Res.string.project_settings_breadcrumb_home.get(),
		)
		Text(
			text = Res.string.project_settings_breadcrumb_root.get(),
			style = MaterialTheme.typography.headlineSmall,
			color = MaterialTheme.colorScheme.onSurface,
			maxLines = 1,
			overflow = TextOverflow.Ellipsis,
			modifier = Modifier.weight(1f),
		)
		AutosaveBadge()
	}
}

/** Says there is no Save button: every change applies as it's made. */
@Composable
private fun AutosaveBadge() {
	val success = LocalHammerColors.current.success
	Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
		Box(modifier = Modifier.size(6.dp).background(success, RectangleShape))
		HdMetaText(text = Res.string.project_settings_autosaved.get())
	}
}
