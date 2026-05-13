package com.darkrockstudios.apps.hammer.android

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ExperimentalComposeApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.lifecycleScope
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.arkivanov.decompose.retainedComponent
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.getAndUpdate
import com.darkrockstudios.apps.hammer.android.widgets.AddNoteActivity
import com.darkrockstudios.apps.hammer.common.components.projectselection.ProjectSelection
import com.darkrockstudios.apps.hammer.common.components.projectselection.ProjectSelectionComponent
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdBottomBar
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMonoLabel
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdNavRail
import com.darkrockstudios.apps.hammer.common.compose.theme.AppTheme
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsRepository
import com.darkrockstudios.apps.hammer.common.data.globalsettings.UiTheme
import com.darkrockstudios.apps.hammer.common.platformMainDispatcher
import com.darkrockstudios.apps.hammer.common.projectselection.ProjectSelectionUi
import com.darkrockstudios.apps.hammer.common.projectselection.toHdBottomBarDestination
import com.darkrockstudios.apps.hammer.common.projectselection.toHdNavRailDestination
import com.darkrockstudios.apps.hammer.common.util.getAppVersionString
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject

@ExperimentalMaterialApi
@ExperimentalComposeApi
class ProjectSelectActivity : AppCompatActivity() {

	private val globalSettingsRepository: GlobalSettingsRepository by inject()
	private val globalSettings = MutableValue(globalSettingsRepository.globalSettings)
	private var settingsUpdateJob: Job? = null

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()

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
				ProjectSelectContent(component)
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
			globalSettingsRepository.globalSettingsUpdates.collect { settings ->
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

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun ProjectSelectContent(component: ProjectSelection) {
	val windowSizeClass = calculateWindowSizeClass()

	when (windowSizeClass.widthSizeClass) {
		WindowWidthSizeClass.Compact -> {
			CompactNavigation(component)
		}

		WindowWidthSizeClass.Medium,
		WindowWidthSizeClass.Expanded -> {
			RailNavigation(component)
		}
	}
}

@OptIn(ExperimentalMaterialApi::class, ExperimentalComposeApi::class)
@Composable
private fun CompactNavigation(
	component: ProjectSelection,
) {
	val stackState by component.stack.subscribeAsState()
	Scaffold(
		modifier = Modifier.defaultScaffold(),
		contentWindowInsets = WindowInsets(0, 0, 0, 0),
		content = { scaffoldPadding ->
			ProjectSelectionUi(
				component,
				modifier = Modifier.rootElement(scaffoldPadding),
			)
		},
		bottomBar = {
			val destinations = ProjectSelection.Locations.entries.map { it.toHdBottomBarDestination() }
			HdBottomBar(
				destinations = destinations,
				selectedId = stackState.active.configuration.location,
				onSelect = { component.showLocation(it) },
			)
		},
	)
}

@OptIn(ExperimentalMaterialApi::class, ExperimentalComposeApi::class)
@Composable
private fun RailNavigation(
	component: ProjectSelection
) {
	val stackState by component.stack.subscribeAsState()
	val navRailState by component.navRailState.subscribeAsState()
	Scaffold(
		modifier = Modifier.defaultScaffold(),
		contentWindowInsets = WindowInsets(0, 0, 0, 0),
		content = { scaffoldPadding ->
			Row(
				modifier = Modifier.rootElement(scaffoldPadding),
			) {
				val destinations =
					ProjectSelection.Locations.entries.map { it.toHdNavRailDestination() }
				HdNavRail(
					destinations = destinations,
					selectedId = stackState.active.configuration.location,
					onSelect = { component.showLocation(it) },
					expanded = navRailState.expanded,
					onToggleExpanded = { component.toggleNavRailExpanded() },
					footer = {
						val versionText = remember { getAppVersionString() }
						HdMonoLabel(
							text = versionText,
							color = MaterialTheme.colorScheme.onSurfaceVariant,
						)
					},
				)

				ProjectSelectionUi(component)
			}
		},
	)
}