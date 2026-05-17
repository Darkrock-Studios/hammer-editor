package com.darkrockstudios.apps.hammer.common.projectselection.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.common.components.projectselection.accountsettings.AccountSettings
import com.darkrockstudios.apps.hammer.common.compose.LocalScreenCharacteristic
import com.darkrockstudios.apps.hammer.common.compose.RootSnackbarHostState
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.designsystem.*
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.compose.theme.LocalHammerColors
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettings
import com.darkrockstudios.apps.hammer.common.data.globalsettings.InitialProjectScreen
import com.darkrockstudios.apps.hammer.common.data.globalsettings.UiTheme
import com.darkrockstudios.apps.hammer.common.getDataVersion
import com.darkrockstudios.apps.hammer.common.projectselection.settings.backups.BackupsSettingsUi

private val MaxColumnWidth = 880.dp
private const val SECTION_COUNT = 7

@Composable
internal fun AccountSettingsUi(
	component: AccountSettings,
	rootSnackbar: RootSnackbarHostState,
	modifier: Modifier = Modifier,
) {
	val state by component.state.subscribeAsState()
	val scope = rememberCoroutineScope()
	val screen = LocalScreenCharacteristic.current
	val isCompact = screen.windowWidthClass == WindowWidthSizeClass.Compact

	val outerHorizontal: Dp = if (isCompact) Ui.Padding.XL else 56.dp
	val outerVertical: Dp = if (isCompact) Ui.Padding.L else 28.dp

	Column(
		modifier = modifier
			.fillMaxSize()
			.background(MaterialTheme.colorScheme.surface),
	) {
		Breadcrumb(isCompact = isCompact)
		HdFolioDivider()

		Column(
			modifier = Modifier
				.weight(1f)
				.fillMaxWidth()
				.verticalScroll(rememberScrollState())
				.padding(horizontal = outerHorizontal, vertical = outerVertical),
		) {
			Box(
				modifier = Modifier
					.widthIn(max = MaxColumnWidth)
					.fillMaxWidth()
					.align(Alignment.CenterHorizontally),
			) {
				Column(verticalArrangement = Arrangement.spacedBy(64.dp)) {
					Hero(
						loggedIn = state.serverIsLoggedIn,
						email = state.currentEmail,
						isCompact = isCompact,
					)

					HdHairlineSection(
						section = 1,
						title = Res.string.settings_theme_label.get(),
						headerTrailing = {
							HdMonoLabel(text = uiThemeLabel(state.uiTheme))
						},
						contentSpacing = 12.dp,
					) {
						HdHairlineSegmentedPicker(
							options = UiTheme.entries,
							selected = state.uiTheme,
							onSelect = { component.setUiTheme(it) },
							label = { uiThemeLabel(it) },
						)
					}

					HdHairlineSection(
						section = 2,
						title = Res.string.settings_initial_screen_label.get(),
						headerTrailing = {
							HdMonoLabel(text = initialScreenLabel(state.initialProjectScreen))
						},
						contentSpacing = 12.dp,
					) {
						HdHairlineSegmentedPicker(
							options = InitialProjectScreen.entries,
							selected = state.initialProjectScreen,
							onSelect = { component.setInitialProjectScreen(it) },
							label = { initialScreenLabel(it) },
						)
					}

					HdHairlineSection(
						section = 3,
						title = Res.string.settings_spellcheck_heading.get(),
						contentSpacing = 18.dp,
					) {
						SpellCheckSettingsContent(component.spellCheckSettings)
					}

					ServerSettingsUi(
						component = component,
						scope = scope,
						rootSnackbar = rootSnackbar,
					)

					HdHairlineSection(
						section = 5,
						title = Res.string.settings_backups_header.get(),
						headerTrailing = {
							HdMonoLabel(
								text = "${state.maxBackups} / ${GlobalSettings.MAX_BACKUPS}",
							)
						},
						contentSpacing = 16.dp,
					) {
						BackupsSettingsUi(component, scope)
					}

					HdHairlineSection(
						section = 6,
						title = Res.string.settings_platform_settings_title.get(),
						contentSpacing = 16.dp,
					) {
						PlatformSettingsUi(component.platformSettings)
					}

					HdHairlineSection(
						section = 7,
						title = Res.string.settings_example_project_header.get(),
						contentSpacing = 14.dp,
					) {
						ExampleProjectSection(component, rootSnackbar)
					}

					Spacer(Modifier.height(8.dp))
				}
			}
		}

		FolioCaption(
			horizontalPadding = outerHorizontal,
		)
	}
}

@Composable
private fun Breadcrumb(
	isCompact: Boolean,
) {
	val horizontal: Dp = if (isCompact) Ui.Padding.XL else 56.dp
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = horizontal, vertical = 14.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(12.dp),
	) {
		HdMonoLabel(
			text = "HAMMER",
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
		HdMonoLabel(
			text = "/",
			color = MaterialTheme.colorScheme.outlineVariant,
		)
		HdMonoLabel(
			text = "ACCOUNT",
			color = MaterialTheme.colorScheme.onSurface,
		)
		Spacer(Modifier.weight(1f))
		AutosaveBadge()
	}
}

@Composable
private fun AutosaveBadge() {
	val success = LocalHammerColors.current.success
	Row(
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(8.dp),
	) {
		Box(
			modifier = Modifier
				.size(6.dp)
				.background(success, RectangleShape),
		)
		HdMonoLabel(
			text = Res.string.project_settings_autosaved.get(),
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
	}
}

@Composable
private fun Hero(
	loggedIn: Boolean,
	email: String?,
	isCompact: Boolean,
) {
	val subtitle = buildString {
		append("v")
		append(getDataVersion())
		append(" · ")
		if (loggedIn && !email.isNullOrBlank()) {
			append("CONNECTED · ${email.uppercase()}")
		} else if (loggedIn) {
			append("CONNECTED")
		} else {
			append("LOCAL INSTALL")
		}
	}
	Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
		HdMonoLabel(text = "§ 0 · ACCOUNT")
		Text(
			text = "Hammer",
			style = if (isCompact) MaterialTheme.typography.displaySmall
			else MaterialTheme.typography.displayMedium,
			color = MaterialTheme.colorScheme.onSurface,
			maxLines = 1,
			overflow = TextOverflow.Ellipsis,
		)
		HdMonoLabel(
			text = subtitle,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
	}
}

@Composable
private fun ExampleProjectSection(
	component: AccountSettings,
	rootSnackbar: RootSnackbarHostState,
) {
	val successMessage = Res.string.settings_example_project_success_message.get()
	Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
		Text(
			text = Res.string.settings_example_project_description.get(),
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurface,
		)
		HdHairlineButton(
			label = Res.string.settings_example_project_button.get(),
			onClick = {
				component.reinstallExampleProject {
					rootSnackbar.showSnackbar(successMessage)
				}
			},
		)
	}
}

@Composable
private fun FolioCaption(
	horizontalPadding: Dp,
) {
	HorizontalDivider(
		thickness = Dp.Hairline,
		color = MaterialTheme.colorScheme.outlineVariant,
	)
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = horizontalPadding, vertical = 10.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
	) {
		HdMonoLabel(text = "ACCOUNT SETTINGS")
		HdMonoLabel(
			text = "·",
			color = MaterialTheme.colorScheme.outlineVariant,
		)
		HdMonoLabel(text = "§§ $SECTION_COUNT")
	}
}

private fun uiThemeLabel(theme: UiTheme): String = when (theme) {
	UiTheme.Light -> "LIGHT"
	UiTheme.Dark -> "DARK"
	UiTheme.FollowSystem -> "SYSTEM"
}

@Composable
private fun initialScreenLabel(screen: InitialProjectScreen): String = when (screen) {
	InitialProjectScreen.Home -> Res.string.settings_initial_screen_home.get()
	InitialProjectScreen.Editor -> Res.string.settings_initial_screen_editor.get()
}
