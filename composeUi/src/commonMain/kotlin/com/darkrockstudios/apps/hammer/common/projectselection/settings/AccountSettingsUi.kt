package com.darkrockstudios.apps.hammer.common.projectselection.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.components.projectselection.accountsettings.AccountSettings
import com.darkrockstudios.apps.hammer.common.compose.LocalScreenCharacteristic
import com.darkrockstudios.apps.hammer.common.compose.MpScrollBarColumn
import com.darkrockstudios.apps.hammer.common.compose.RootSnackbarHostState
import com.darkrockstudios.apps.hammer.common.compose.Toaster
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.WindowWidthSizeClass
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdFolioDivider
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineButton
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineSegmentedPicker
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMetaText
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdSettingGroup
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdSettingRow
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdTabPill
import com.darkrockstudios.apps.hammer.common.compose.designsystem.hdColumnMaxWidth
import com.darkrockstudios.apps.hammer.common.compose.plugin.PluginUiRegistry
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.compose.scrollBarOverlay
import com.darkrockstudios.apps.hammer.common.compose.theme.LocalHammerColors
import com.darkrockstudios.apps.hammer.common.compose.theme.oledBlackThemeAvailable
import com.darkrockstudios.apps.hammer.common.data.globalsettings.InitialProjectScreen
import com.darkrockstudios.apps.hammer.common.data.globalsettings.UiTheme
import com.darkrockstudios.apps.hammer.common.projectselection.settings.backups.BackupsSettingsUi
import com.darkrockstudios.apps.hammer.plugins.wasmhost.RuntimePlugins
import com.darkrockstudios.apps.hammer.project_select_nav_account_settings
import com.darkrockstudios.apps.hammer.project_settings_autosaved
import com.darkrockstudios.apps.hammer.settings_example_project_button
import com.darkrockstudios.apps.hammer.settings_example_project_success_message
import com.darkrockstudios.apps.hammer.settings_group_appearance
import com.darkrockstudios.apps.hammer.settings_group_device
import com.darkrockstudios.apps.hammer.settings_group_spellcheck
import com.darkrockstudios.apps.hammer.settings_initial_screen_editor
import com.darkrockstudios.apps.hammer.settings_initial_screen_home
import com.darkrockstudios.apps.hammer.settings_row_example_project
import com.darkrockstudios.apps.hammer.settings_row_example_project_desc
import com.darkrockstudios.apps.hammer.settings_row_initial_screen
import com.darkrockstudios.apps.hammer.settings_row_theme
import com.darkrockstudios.apps.hammer.settings_tab_general
import com.darkrockstudios.apps.hammer.settings_tab_plugins
import com.darkrockstudios.apps.hammer.settings_tab_sync
import com.darkrockstudios.apps.hammer.settings_tab_writing
import com.darkrockstudios.apps.hammer.settings_theme_dark
import com.darkrockstudios.apps.hammer.settings_theme_followsystem
import com.darkrockstudios.apps.hammer.settings_theme_light
import com.darkrockstudios.apps.hammer.settings_theme_oled_black
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
) {
	val scope = rememberCoroutineScope()
	val screen = LocalScreenCharacteristic.current
	val isCompact = screen.windowWidthClass == WindowWidthSizeClass.Compact
	val horizontal: Dp = if (isCompact) Ui.Padding.XL else 56.dp
	val tabs = SettingsTab.entries.filter { it != SettingsTab.Plugins || runtimePlugins != null }
	var tab by rememberSaveable { mutableStateOf(initialTab) }

	Toaster(component, rootSnackbar)

	Column(
		modifier = modifier
			.fillMaxSize()
			.background(MaterialTheme.colorScheme.surface),
	) {
		Masthead(horizontal)
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

		// A fresh scroll position per tab, so switching tabs always starts at the top.
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
						when (tab) {
							SettingsTab.General -> GeneralTab(component, rootSnackbar)
							SettingsTab.Writing -> WritingTab(component)
							SettingsTab.Sync -> SyncSettingsTab(component, scope)
							SettingsTab.Plugins -> runtimePlugins?.let { PluginsTab(it) }
						}
						Spacer(Modifier.height(8.dp))
					}
				}
				MpScrollBarColumn(modifier = scrollBarOverlay(), state = scrollState)
			}
		}
	}

	// Server setup can raise these while any tab shows, so they live above the tabs.
	ServerSetupDialog(component, scope)
	MergeProjectsDialog(component)
	TermsOfServiceDialog(component)
}

@Composable
private fun Masthead(horizontal: Dp) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.height(Ui.TOP_BAR_HEIGHT)
			.padding(horizontal = horizontal),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Text(
			text = Res.string.project_select_nav_account_settings.get(),
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
}

@Composable
private fun ColumnScope.WritingTab(component: AccountSettings) {
	HdSettingGroup(title = Res.string.settings_group_spellcheck.get()) {
		SpellCheckSettingsContent(component.spellCheckSettings)
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
