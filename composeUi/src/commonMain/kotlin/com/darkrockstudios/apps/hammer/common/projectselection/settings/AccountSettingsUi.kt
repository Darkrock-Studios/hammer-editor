package com.darkrockstudios.apps.hammer.common.projectselection.settings

import com.darkrockstudios.apps.hammer.account_settings_folio_caption
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.components.projectselection.accountsettings.AccountSettings
import com.darkrockstudios.apps.hammer.common.compose.RootSnackbarHostState
import com.darkrockstudios.apps.hammer.common.compose.Toaster
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineButton
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineSegmentedPicker
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineToggleRow
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdSettingGroup
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdSettingRow
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdTabbedPage
import com.darkrockstudios.apps.hammer.common.compose.plugin.PluginUiRegistry
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.compose.theme.oledBlackThemeAvailable
import com.darkrockstudios.apps.hammer.common.data.globalsettings.InitialProjectScreen
import com.darkrockstudios.apps.hammer.common.data.globalsettings.UiTheme
import com.darkrockstudios.apps.hammer.common.projectselection.settings.backups.BackupsSettingsUi
import com.darkrockstudios.apps.hammer.plugins.wasmhost.RuntimePlugins
import com.darkrockstudios.apps.hammer.project_select_nav_account_settings
import com.darkrockstudios.apps.hammer.settings_example_project_button
import com.darkrockstudios.apps.hammer.settings_example_project_success_message
import com.darkrockstudios.apps.hammer.settings_group_appearance
import com.darkrockstudios.apps.hammer.settings_group_device
import com.darkrockstudios.apps.hammer.settings_group_punctuation
import com.darkrockstudios.apps.hammer.settings_group_spellcheck
import com.darkrockstudios.apps.hammer.settings_group_tips
import com.darkrockstudios.apps.hammer.settings_initial_screen_editor
import com.darkrockstudios.apps.hammer.settings_initial_screen_home
import com.darkrockstudios.apps.hammer.settings_row_example_project
import com.darkrockstudios.apps.hammer.settings_row_example_project_desc
import com.darkrockstudios.apps.hammer.settings_row_initial_screen
import com.darkrockstudios.apps.hammer.settings_row_theme
import com.darkrockstudios.apps.hammer.settings_smart_punctuation_enable
import com.darkrockstudios.apps.hammer.settings_smart_punctuation_hint
import com.darkrockstudios.apps.hammer.settings_tab_general
import com.darkrockstudios.apps.hammer.settings_tab_plugins
import com.darkrockstudios.apps.hammer.settings_tab_sync
import com.darkrockstudios.apps.hammer.settings_tab_writing
import com.darkrockstudios.apps.hammer.settings_theme_dark
import com.darkrockstudios.apps.hammer.settings_theme_followsystem
import com.darkrockstudios.apps.hammer.settings_theme_light
import com.darkrockstudios.apps.hammer.settings_theme_oled_black
import com.darkrockstudios.apps.hammer.settings_tips_progress
import com.darkrockstudios.apps.hammer.settings_tips_reset_button
import com.darkrockstudios.apps.hammer.settings_tips_reset_description
import com.darkrockstudios.apps.hammer.settings_tips_reset_title
import com.darkrockstudios.apps.hammer.settings_tips_show
import com.darkrockstudios.apps.hammer.settings_tips_show_hint
import org.jetbrains.compose.resources.pluralStringResource
import org.koin.compose.koinInject

private val MaxColumnWidth = 720.dp

/** The tabs of app Settings. Plugins shows only where plugins can be installed. */
enum class SettingsTab { General, Writing, Sync, Plugins }

/**
 * App Settings as four tabs of setting rows. The masthead names the screen as the nav does;
 * connection, account and version each show once, where they belong.
 */
@Composable
internal fun AccountSettingsUi(
	component: AccountSettings,
	rootSnackbar: RootSnackbarHostState,
	modifier: Modifier = Modifier,
	runtimePlugins: RuntimePlugins? = null,
	initialTab: SettingsTab = SettingsTab.General,
	/** The Plugins tab; left out where plugins cannot be installed. */
	pluginsTab: (@Composable ColumnScope.() -> Unit)? = runtimePlugins?.let { plugins -> { PluginsTab(plugins) } },
) {
	val scope = rememberCoroutineScope()
	val tabs = SettingsTab.entries.filter { it != SettingsTab.Plugins || pluginsTab != null }
	var tab by rememberSaveable { mutableStateOf(initialTab) }

	Toaster(component, rootSnackbar)

	HdTabbedPage(
		title = Res.string.project_select_nav_account_settings.get(),
		tabs = tabs,
		selected = tab,
		label = { tabLabel(it) },
		onSelect = { tab = it },
		maxWidth = MaxColumnWidth,
		modifier = modifier,
		autosaved = true,
		folio = listOf(Res.string.account_settings_folio_caption.get(), "§§ ${tabs.size}"),
	) {
		when (tab) {
			SettingsTab.General -> GeneralTab(component, rootSnackbar)
			SettingsTab.Writing -> WritingTab(component)
			SettingsTab.Sync -> SyncSettingsTab(component, scope)
			SettingsTab.Plugins -> pluginsTab?.invoke(this)
		}
	}

	// Server setup can raise these while any tab shows, so they live above the tabs.
	ServerSetupDialog(component, scope)
	MergeProjectsDialog(component)
	TermsOfServiceDialog(component)
}

@Composable
private fun ColumnScope.GeneralTab(component: AccountSettings, rootSnackbar: RootSnackbarHostState) {
	val state by component.state.subscribeAsState()
	HdSettingGroup(title = Res.string.settings_group_appearance.get()) {
		HdSettingRow(title = Res.string.settings_row_theme.get()) {
			HdHairlineSegmentedPicker(
				options = UiTheme.entries.filter { it != UiTheme.OledBlack || oledBlackThemeAvailable },
				selected = state.uiTheme,
				onSelect = { component.setUiTheme(it) },
				label = { uiThemeLabel(it) },
				compact = true,
			)
		}
		HdSettingRow(title = Res.string.settings_row_initial_screen.get()) {
			HdHairlineSegmentedPicker(
				options = InitialProjectScreen.entries,
				selected = state.initialProjectScreen,
				onSelect = { component.setInitialProjectScreen(it) },
				label = { initialScreenLabel(it) },
				compact = true,
			)
		}
	}

	HdSettingGroup(title = Res.string.settings_group_device.get()) {
		PlatformSettingsUi(component.platformSettings)
		val successMessage = Res.string.settings_example_project_success_message.get()
		HdSettingRow(
			title = Res.string.settings_row_example_project.get(),
			description = Res.string.settings_row_example_project_desc.get(),
		) {
			HdHairlineButton(
				label = Res.string.settings_example_project_button.get(),
				onClick = { component.reinstallExampleProject { rootSnackbar.showSnackbar(successMessage) } },
			)
		}
	}

	HdSettingGroup(title = Res.string.settings_group_tips.get()) {
		HdHairlineToggleRow(
			checked = state.tipsEnabled,
			onCheckedChange = { component.setTipsEnabled(it) },
			label = Res.string.settings_tips_show.get(),
			hint = Res.string.settings_tips_show_hint.get(),
		)
		HdSettingRow(
			title = Res.string.settings_tips_reset_title.get(),
			description = Res.string.settings_tips_reset_description.get(),
			meta = state.tipsProgress?.let { pluralStringResource(Res.plurals.settings_tips_progress, it.total, it.seen, it.total) },
		) {
			HdHairlineButton(
				label = Res.string.settings_tips_reset_button.get(),
				onClick = { component.resetTips() },
			)
		}
	}
}

@Composable
private fun ColumnScope.WritingTab(component: AccountSettings) {
	val state by component.state.subscribeAsState()
	HdSettingGroup(title = Res.string.settings_group_spellcheck.get()) {
		SpellCheckSettingsContent(component.spellCheckSettings)
	}
	HdSettingGroup(title = Res.string.settings_group_punctuation.get()) {
		HdHairlineToggleRow(
			checked = state.smartPunctuation,
			onCheckedChange = component::setSmartPunctuation,
			label = Res.string.settings_smart_punctuation_enable.get(),
			hint = Res.string.settings_smart_punctuation_hint.get(),
		)
	}
}

/** The plugin manager itself, not a button that opens it. */
@Composable
private fun ColumnScope.PluginsTab(runtimePlugins: RuntimePlugins) {
	Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
		PluginSettingsSection(koinInject<PluginUiRegistry>().settingsPanes(), runtimePlugins)
	}
}

@Composable
private fun ColumnScope.SyncSettingsTab(component: AccountSettings, scope: kotlinx.coroutines.CoroutineScope) {
	SyncSettingsContent(component, scope)
	BackupsSettingsUi(component, scope)
	RemoveServerRow(component, scope)
}

@Composable
private fun tabLabel(tab: SettingsTab): String = when (tab) {
	SettingsTab.General -> Res.string.settings_tab_general.get()
	SettingsTab.Writing -> Res.string.settings_tab_writing.get()
	SettingsTab.Sync -> Res.string.settings_tab_sync.get()
	SettingsTab.Plugins -> Res.string.settings_tab_plugins.get()
}

@Composable
private fun uiThemeLabel(theme: UiTheme): String = when (theme) {
	UiTheme.Light -> Res.string.settings_theme_light.get()
	UiTheme.Dark -> Res.string.settings_theme_dark.get()
	UiTheme.OledBlack -> Res.string.settings_theme_oled_black.get()
	UiTheme.FollowSystem -> Res.string.settings_theme_followsystem.get()
}

@Composable
private fun initialScreenLabel(screen: InitialProjectScreen): String = when (screen) {
	InitialProjectScreen.Home -> Res.string.settings_initial_screen_home.get()
	InitialProjectScreen.Editor -> Res.string.settings_initial_screen_editor.get()
}
