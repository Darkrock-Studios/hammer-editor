package com.darkrockstudios.apps.hammer.common.projectselection.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.MR
import com.darkrockstudios.apps.hammer.common.components.projectselection.accountsettings.AccountSettings
import com.darkrockstudios.apps.hammer.common.compose.ExposedDropDown
import com.darkrockstudios.apps.hammer.common.compose.RootSnackbarHostState
import com.darkrockstudios.apps.hammer.common.compose.SpacerL
import com.darkrockstudios.apps.hammer.common.compose.SpacerXL
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.moko.get
import com.darkrockstudios.apps.hammer.common.data.globalsettings.UiTheme
import com.darkrockstudios.apps.hammer.common.getDataVersion
import dev.icerock.moko.resources.compose.stringResource

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
			shape = containerShape
		) {
			Column(
				modifier = Modifier
					.fillMaxSize()
					.padding(horizontal = Ui.Padding.XL)
			) {
				Text(
					MR.strings.settings_heading.get(),
					style = MaterialTheme.typography.headlineLarge,
					color = MaterialTheme.colorScheme.onBackground,
				)

				Divider(modifier = Modifier.fillMaxWidth())

				Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
					SpacerL()

					Column(modifier = Modifier.padding(Ui.Padding.L)) {
						val themeOptions = remember { UiTheme.entries }
						ExposedDropDown(
							modifier = Modifier.defaultMinSize(minWidth = 256.dp),
							label = MR.strings.settings_theme_label.get(),
							items = themeOptions,
							selectedItem = state.uiTheme,
						) { selectedTheme ->
							if (selectedTheme != null) {
								component.setUiTheme(selectedTheme)
							}
						}
					}

					SpacerXL()

					Column(modifier = Modifier.padding(Ui.Padding.M)) {
						Text(
							MR.strings.settings_example_project_header.get(),
							style = MaterialTheme.typography.headlineSmall,
							color = MaterialTheme.colorScheme.onBackground,
						)
						Text(
							MR.strings.settings_example_project_description.get(),
							style = MaterialTheme.typography.bodySmall,
							color = MaterialTheme.colorScheme.onBackground,
							fontStyle = FontStyle.Italic
						)

						Spacer(modifier = Modifier.size(Ui.Padding.M))

						val successMessage = MR.strings.settings_example_project_success_message.get()
						Button(onClick = {
							component.reinstallExampleProject {
								rootSnackbar.showSnackbar(successMessage)
							}
						}) {
							Text(MR.strings.settings_example_project_button.get())
						}
					}

					SpacerXL()

					ServerSettingsUi(component, scope, rootSnackbar)

					SpacerXL()

					PlatformSettingsUi(component.platformSettings)

					SpacerXL()

					Text(
						stringResource(MR.strings.settings_data_version, getDataVersion()),
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.onBackground,
					)

					SpacerXL()
				}
			}
		}
	}
}