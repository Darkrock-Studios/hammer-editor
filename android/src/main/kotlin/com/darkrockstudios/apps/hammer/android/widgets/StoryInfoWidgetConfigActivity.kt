package com.darkrockstudios.apps.hammer.android.widgets

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.lifecycleScope
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.arkivanov.decompose.value.MutableValue
import com.darkrockstudios.apps.hammer.android.R
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdButtonBar
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdFolioDivider
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMasthead
import com.darkrockstudios.apps.hammer.common.compose.serializableStateSaver
import com.darkrockstudios.apps.hammer.common.compose.theme.AppTheme
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsRepository
import com.darkrockstudios.apps.hammer.common.data.globalsettings.UiTheme
import com.darkrockstudios.apps.hammer.common.data.projectdata.loadStoredProjectData
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import io.github.aakira.napier.Napier
import kotlinx.coroutines.launch
import net.peanuuutz.tomlkt.Toml
import okio.FileSystem
import org.koin.android.ext.android.inject

private val ContentMaxWidth = 480.dp

class StoryInfoWidgetConfigActivity : ComponentActivity() {
	private val globalSettingsRepository: GlobalSettingsRepository by inject()
	private val globalSettings = MutableValue(globalSettingsRepository.globalSettings)
	private val projectsRepository: ProjectsRepository by inject()
	private val fileSystem: FileSystem by inject()
	private val toml: Toml by inject()

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()

		if (intent.action != "android.appwidget.action.APPWIDGET_CONFIGURE") {
			Napier.e("StoryInfoWidgetConfigActivity launched with bad Intent")
			finish()
		}

		val appWidgetId = intent?.extras?.getInt(
			AppWidgetManager.EXTRA_APPWIDGET_ID,
			AppWidgetManager.INVALID_APPWIDGET_ID
		) ?: AppWidgetManager.INVALID_APPWIDGET_ID
		if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
			Napier.e("StoryInfoWidgetConfigActivity launched with invalid widget ID")
			finish()
		} else {
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
				settings = settingsState,
				useDarkTheme = isDark,
				getOverrideColorScheme = ::getDynamicColorScheme
			) {
				ConfigUi(
					widgetId = appWidgetId,
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

	private suspend fun save(widgetId: Int, projectDef: ProjectDef) {
		val accentHex = loadStoredProjectData(projectDef, fileSystem, toml).data.theme?.primary

		widgetConfigDataStore.updateData {
			it.saveWidgetConfig(widgetId, projectDef, accentHex)
		}

		val manager = GlanceAppWidgetManager(this)
		val glanceId = manager.getGlanceIdBy(widgetId)
		StoryInfoWidget().update(this, glanceId)

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
	widgetId: Int,
	projects: List<ProjectDef>,
	onSave: (projectDef: ProjectDef) -> Unit,
	onCancel: () -> Unit,
) {
	var selectedProject by rememberSaveable(
		saver = serializableStateSaver(ProjectDef.serializer())
	) {
		mutableStateOf(projects.first())
	}

	Surface(
		modifier = Modifier.fillMaxSize(),
		color = MaterialTheme.colorScheme.surface,
		contentColor = MaterialTheme.colorScheme.onSurface,
	) {
		Column(
			modifier = Modifier
				.fillMaxSize()
				.systemBarsPadding()
				.imePadding(),
		) {
			HdMasthead(
				section = "WIDGET CONFIG",
				leadingMeta = listOf("ID $widgetId"),
			)
			HdFolioDivider()

			Column(
				modifier = Modifier
					.fillMaxWidth()
					.weight(1f)
					.verticalScroll(rememberScrollState())
					.padding(horizontal = Ui.Padding.XL, vertical = Ui.Padding.L),
				horizontalAlignment = Alignment.CenterHorizontally,
			) {
				Column(
					modifier = Modifier
						.fillMaxWidth()
						.widthIn(max = ContentMaxWidth),
					verticalArrangement = Arrangement.spacedBy(Ui.Padding.L),
				) {
					Text(
						text = stringResource(R.string.story_info_widget_config_title),
						style = MaterialTheme.typography.headlineSmall,
						color = MaterialTheme.colorScheme.onSurface,
					)

					ProjectDropDownUi(
						projects = projects,
						onProjectSelected = { selectedProject = it },
					)
				}
			}

			HdButtonBar(
				cancelLabel = stringResource(R.string.note_widget_dialog_cancel_button),
				primaryLabel = stringResource(R.string.note_widget_dialog_save_button),
				onCancel = onCancel,
				onPrimary = { onSave(selectedProject) },
			)
		}
	}
}
