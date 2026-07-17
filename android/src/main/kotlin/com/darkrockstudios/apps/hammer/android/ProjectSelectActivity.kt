package com.darkrockstudios.apps.hammer.android

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.ExperimentalComposeApi
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.lifecycleScope
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.arkivanov.decompose.retainedComponent
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.getAndUpdate
import com.darkrockstudios.apps.hammer.android.widgets.AddNoteActivity
import com.darkrockstudios.apps.hammer.common.components.projectselection.ProjectSelectionComponent
import com.darkrockstudios.apps.hammer.common.compose.theme.AppTheme
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.globalsettings.UiTheme
import com.darkrockstudios.apps.hammer.common.platformMainDispatcher
import com.darkrockstudios.apps.hammer.common.projectselection.ProjectSelectScaffold
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.init
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject

@ExperimentalMaterialApi
@ExperimentalComposeApi
class ProjectSelectActivity : AppCompatActivity() {

	private val globalSettingsStore: GlobalSettingsStore by inject()
	private val globalSettings = MutableValue(globalSettingsStore.globalSettings)
	private var settingsUpdateJob: Job? = null

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()

		FileKit.init(this)

		handleIntent(intent)

		val component = retainedComponent { componentContext ->
			ProjectSelectionComponent(
				componentContext = componentContext,
				onProjectSelected = ::onProjectSelected
			)
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
				settings = settingsState,
				useDarkTheme = isDark,
				getOverrideColorScheme = ::getDynamicColorScheme
			) {
				ProjectSelectScaffold(component)
			}
		}
	}

	private fun handleIntent(intent: Intent?) {
		if (intent != null) {
			if (intent.action == Intent.ACTION_CREATE_NOTE) {
				startActivity(Intent(this, AddNoteActivity::class.java))
				finish()
			}
		}
	}

	override fun onStart() {
		super.onStart()

		settingsUpdateJob = lifecycleScope.launch {
			globalSettingsStore.globalSettingsUpdates.collect { settings ->
				withContext(platformMainDispatcher) {
					globalSettings.getAndUpdate { settings }
				}
			}
		}
	}

	override fun onStop() {
		super.onStop()
		settingsUpdateJob?.cancel()
		settingsUpdateJob = null
	}

	private fun onProjectSelected(projectDef: ProjectDef) {
		startActivity(ProjectRootActivity.createIntent(this, projectDef))
	}
}
