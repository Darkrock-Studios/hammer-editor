package com.darkrockstudios.apps.hammer.android.widgets

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.lifecycleScope
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState
import com.arkivanov.decompose.value.MutableValue
import com.darkrockstudios.apps.hammer.android.R
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.theme.AppTheme
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsRepository
import com.darkrockstudios.apps.hammer.common.data.globalsettings.UiTheme
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.soywiz.korio.async.launch
import io.github.aakira.napier.Napier
import org.koin.android.ext.android.inject

class AddNoteWidgetConfigActivity : ComponentActivity() {
	private val globalSettingsRepository: GlobalSettingsRepository by inject()
	private val globalSettings = MutableValue(globalSettingsRepository.globalSettings)
	private val projectsRepository: ProjectsRepository by inject()

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		if (intent.action != "android.appwidget.action.APPWIDGET_CONFIGURE") {
			Napier.e("AddNoteWidgetConfigActivity launched with bad Intent")
			finish()
		}

		val appWidgetId = intent?.extras?.getInt(
			AppWidgetManager.EXTRA_APPWIDGET_ID,
			AppWidgetManager.INVALID_APPWIDGET_ID
		) ?: AppWidgetManager.INVALID_APPWIDGET_ID
		if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
			Napier.e("AddNoteWidgetConfigActivity launched with invalid widget ID")
			finish()
		} else {
			// Set this early so if the Activity is finished in any other way than save(), it will be
			// considered canceled
			setCancel(appWidgetId)
		}

		val projects = projectsRepository.getProjects()
		if (projects.isEmpty()) {
			Toast.makeText(
				this,
				getString(R.string.note_widget_toast_no_projects),
				Toast.LENGTH_SHORT
			).show()
			finish()
		}

		setContent {
			val settingsState by globalSettings.subscribeAsState()
			val isDark = when (settingsState.uiTheme) {
				UiTheme.Light -> false
				UiTheme.Dark -> true
				UiTheme.FollowSystem -> isSystemInDarkTheme()
			}

			// Dynamic color is available on Android 12+
			val localCtx = LocalContext.current
			fun getDynamicColorScheme(useDark: Boolean): ColorScheme? {
				val dynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
				return when {
					dynamicColor && useDark -> dynamicDarkColorScheme(localCtx)
					dynamicColor && !useDark -> dynamicLightColorScheme(localCtx)
					else -> null
				}
			}

			AppTheme(
				useDarkTheme = isDark,
				getOverrideColorScheme = ::getDynamicColorScheme
			) {
				ConfigUi(
					projects = projects,
					onSave = { proj ->
						lifecycleScope.launch {
							save(appWidgetId, proj)
						}
					},
					onCancel = { finish() }
				)
			}
		}
	}

	private suspend fun save(widgetId: Int, projectDef: ProjectDef?) {
		widgetConfigDataStore.updateData {
			// TODO apparently we are supposed to save the glanceId, but it's an opaque class so... 🤷‍♂️
			it.saveWidgetConfig(widgetId, projectDef)
		}

		val manager = GlanceAppWidgetManager(this)
		val glanceId = manager.getGlanceIdBy(widgetId)
		AddNoteWidget().update(this, glanceId)

		val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
		setResult(RESULT_OK, resultValue)

		finish()
	}

	private fun setCancel(widgetId: Int) {
		val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
		setResult(RESULT_CANCELED, resultValue)
	}
}

@Composable
private fun ConfigUi(
	projects: List<ProjectDef>,
	onSave: (projectDef: ProjectDef?) -> Unit,
	onCancel: () -> Unit,
) {
	Column(
		modifier = Modifier.fillMaxSize(),
		horizontalAlignment = Alignment.CenterHorizontally
	) {
		var selectedProject by rememberSaveable { mutableStateOf(projects.first()) }
		var specificProject by rememberSaveable { mutableStateOf(true) }
		Column(
			modifier = Modifier
				.padding(Ui.Padding.XL)
				.width(IntrinsicSize.Max)
		) {
			Text(
				stringResource(R.string.note_widget_config_title),
				style = MaterialTheme.typography.headlineMedium,
				color = MaterialTheme.colorScheme.onBackground,
			)

			Row(verticalAlignment = Alignment.CenterVertically) {
				Checkbox(checked = specificProject, onCheckedChange = { specificProject = it })
				Text(
					text = stringResource(id = R.string.note_widget_config_project_checkbox),
					color = MaterialTheme.colorScheme.onBackground,
				)
			}
			Text(
				text = stringResource(id = R.string.note_widget_config_project_checkbox_explained),
				color = MaterialTheme.colorScheme.onBackground,
				fontStyle = FontStyle.Italic,
			)
			Spacer(modifier = Modifier.size(Ui.Padding.M))
			if (specificProject) {
				ProjectDropDownUi(projects) {
					selectedProject = it
				}
			}

			Spacer(modifier = Modifier.size(Ui.Padding.L))
			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.SpaceBetween
			) {
				Button(onClick = onCancel) {
					Text(stringResource(R.string.note_widget_dialog_cancel_button))
				}

				Button(
					onClick = {
						if (specificProject) {
							onSave(selectedProject)
						} else {
							onSave(null)
						}
					}
				) {
					Text(stringResource(R.string.note_widget_dialog_save_button))
				}
			}
		}
	}
}