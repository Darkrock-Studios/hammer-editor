package com.darkrockstudios.apps.hammer.common.projectselection.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.common.components.projectselection.accountsettings.AccountSettings
import com.darkrockstudios.apps.hammer.common.compose.ExposedDropDown
import com.darkrockstudios.apps.hammer.common.compose.RootSnackbarHostState
import com.darkrockstudios.apps.hammer.common.compose.SpacerXL
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.globalsettings.UiTheme
import com.darkrockstudios.apps.hammer.common.getDataVersion
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
internal fun AccountSettingsUi(
	component: AccountSettings,
	rootSnackbar: RootSnackbarHostState,
	modifier: Modifier = Modifier
) {
	val state by component.state.subscribeAsState()
	val scope = rememberCoroutineScope()

	val windowSizeClass = calculateWindowSizeClass()
	val containerShape = if (windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact) {
		MaterialTheme.shapes.large.copy(
			bottomEnd = CornerSize(0),
			bottomStart = CornerSize(0),
			topEnd = CornerSize(0),
		)
	} else {
		RectangleShape
	}

	val containerElevation = if (windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact) {
		Ui.ToneElevation.SMALL
	} else {
		Ui.ToneElevation.NONE
	}

	Box(modifier = modifier.fillMaxSize()) {
		Surface(
			tonalElevation = containerElevation,
			shape = containerShape,
			modifier = Modifier.fillMaxSize()
		) {
			Column(
				modifier = Modifier
					.fillMaxSize()
			) {
				Text(
					Res.string.settings_heading.get(),
					style = MaterialTheme.typography.headlineLarge,
					color = MaterialTheme.colorScheme.onBackground,
					modifier = Modifier.padding(Ui.Padding.XL)
				)

				Column(
					modifier = Modifier
						.widthIn(max = Ui.MAX_PANEL_WIDTH)
						.verticalScroll(rememberScrollState())
						.padding(horizontal = Ui.Padding.L)
						.padding(bottom = Ui.Padding.XL)
				) {
					SettingsSectionGroup {
						Column(modifier = Modifier.padding(Ui.Padding.M)) {
							Text(
								Res.string.settings_theme_label.get(),
								style = MaterialTheme.typography.headlineSmall,
								color = MaterialTheme.colorScheme.onBackground,
							)
							Spacer(modifier = Modifier.size(Ui.Padding.M))

							val themeOptions = remember { UiTheme.entries }
							ExposedDropDown(
								modifier = Modifier.fillMaxWidth(),
								label = Res.string.settings_theme_label.get(),
								items = themeOptions,
								selectedItem = state.uiTheme,
							) { selectedTheme ->
								if (selectedTheme != null) {
									component.setUiTheme(selectedTheme)
								}
							}
						}
					}

					SpacerXL()

					SettingsSectionGroup {
						SpellCheckSettingsUi(
							component = component.spellCheckSettings,
						)
					}

					SpacerXL()

					SettingsSectionGroup {
						ServerSettingsUi(component, scope, rootSnackbar)
					}

					SpacerXL()

					SettingsSectionGroup {
						BackupsSettingsUi(component, scope)
					}

					SpacerXL()

					SettingsSectionGroup {
						PlatformSettingsUi(component.platformSettings)
					}

					SpacerXL()

					SettingsSectionGroup {
						Column {
							Text(
								Res.string.settings_example_project_header.get(),
								style = MaterialTheme.typography.headlineSmall,
								color = MaterialTheme.colorScheme.onBackground,
							)
							Text(
								Res.string.settings_example_project_description.get(),
								style = MaterialTheme.typography.bodySmall,
								color = MaterialTheme.colorScheme.onBackground,
								fontStyle = FontStyle.Italic
							)

							Spacer(modifier = Modifier.size(Ui.Padding.M))

							val successMessage = Res.string.settings_example_project_success_message.get()
							OutlinedButton(onClick = {
								component.reinstallExampleProject {
									rootSnackbar.showSnackbar(successMessage)
								}
							}) {
								Text(Res.string.settings_example_project_button.get())
							}
						}
					}

					SpacerXL()

					Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
						Text(
							stringResource(Res.string.settings_data_version, getDataVersion()),
							style = MaterialTheme.typography.labelMedium,
							color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
						)
					}
				}
			}
		}
	}
}

@Composable
private fun SettingsSectionGroup(
	modifier: Modifier = Modifier,
	content: @Composable ColumnScope.() -> Unit
) {
	Surface(
		modifier = modifier.fillMaxWidth(),
		shape = RoundedCornerShape(16.dp),
		color = MaterialTheme.colorScheme.surfaceContainerLow,
	) {
		Column(
			modifier = Modifier.padding(Ui.Padding.L),
			content = content
		)
	}
}