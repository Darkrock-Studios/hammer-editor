package com.darkrockstudios.apps.hammer.android

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment.Companion.Start
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.arkivanov.decompose.ExperimentalDecomposeApi
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState
import com.arkivanov.decompose.retainedComponent
import com.arkivanov.decompose.router.slot.ChildSlot
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.getAndUpdate
import com.darkrockstudios.apps.hammer.android.widgets.AddNoteActivity
import com.darkrockstudios.apps.hammer.common.components.projectselection.ProjectSelection
import com.darkrockstudios.apps.hammer.common.components.projectselection.ProjectSelectionComponent
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.moko.get
import com.darkrockstudios.apps.hammer.common.compose.theme.AppTheme
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsRepository
import com.darkrockstudios.apps.hammer.common.data.globalsettings.UiTheme
import com.darkrockstudios.apps.hammer.common.platformMainDispatcher
import com.darkrockstudios.apps.hammer.common.projectselection.ProjectSelectionFab
import com.darkrockstudios.apps.hammer.common.projectselection.ProjectSelectionUi
import com.darkrockstudios.apps.hammer.common.projectselection.getLocationIcon
import com.darkrockstudios.apps.hammer.common.util.getAppVersionString
import com.seiko.imageloader.ImageLoader
import com.seiko.imageloader.LocalImageLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject

@ExperimentalMaterialApi
@ExperimentalComposeApi
class ProjectSelectActivity : AppCompatActivity() {

	private val imageLoader: ImageLoader by inject()
	private val globalSettingsRepository: GlobalSettingsRepository by inject()
	private val globalSettings = MutableValue(globalSettingsRepository.globalSettings)
	private var settingsUpdateJob: Job? = null

	@OptIn(ExperimentalDecomposeApi::class)
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		WindowCompat.setDecorFitsSystemWindows(window, false)

		handleIntent(intent)

		val component = retainedComponent { componentContext ->
			ProjectSelectionComponent(
				componentContext = componentContext,
				onProjectSelected = ::onProjectSelected
			)
		}

		setContent {
			CompositionLocalProvider(
				LocalImageLoader provides imageLoader,
			) {
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
					ProjectSelectContent(component)
				}
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
		val intent = Intent(this, ProjectRootActivity::class.java).apply {
			putExtra(ProjectRootActivity.EXTRA_PROJECT, projectDef)
		}
		startActivity(intent)
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

		WindowWidthSizeClass.Medium -> {
			MediumNavigation(component)
		}

		WindowWidthSizeClass.Expanded -> {
			ExpandedNavigation(component)
		}
	}
}

@OptIn(ExperimentalMaterialApi::class, ExperimentalComposeApi::class)
@Composable
private fun CompactNavigation(
	component: ProjectSelection,
) {
	val slot by component.slot.subscribeAsState()
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
			NavigationBar {
				ProjectSelection.Locations.values().forEach { item ->
					NavigationBarItem(
						selected = item == slot.child?.configuration?.location,
						onClick = { component.showLocation(item) },
						icon = { Icon(imageVector = getLocationIcon(item), contentDescription = item.text.get()) },
					)
				}
			}
		},
		floatingActionButton = {
			ProjectSelectionFab(component)
		}
	)
}

@OptIn(
	ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class, ExperimentalComposeApi::class,
)
@Composable
private fun MediumNavigation(
	component: ProjectSelection
) {
	val slot by component.slot.subscribeAsState()
	Scaffold(
		modifier = Modifier.defaultScaffold(),
		contentWindowInsets = WindowInsets(0, 0, 0, 0),
		content = { scaffoldPadding ->
			Row(
				modifier = Modifier.rootElement(scaffoldPadding),
			) {
				NavigationRail(modifier = Modifier.padding(top = Ui.Padding.M)) {
					ProjectSelection.Locations.values().forEach { item ->
						NavigationRailItem(
							icon = {
								Icon(
									imageVector = getLocationIcon(item),
									contentDescription = item.text.get()
								)
							},
							label = { Text(item.text.get()) },
							selected = item == slot.child?.configuration?.location,
							onClick = { component.showLocation(item) }
						)
					}

					Spacer(modifier = Modifier.weight(1f))

					val versionText = remember { getAppVersionString() }

					Text(
						versionText,
						style = MaterialTheme.typography.labelSmall,
						fontWeight = FontWeight.Thin,
						modifier = Modifier
							.align(Start)
							.padding(Ui.Padding.L)
					)
				}

				ProjectSelectionUi(component)
			}
		},
		floatingActionButton = {
			ProjectSelectionFab(component)
		}
	)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class, ExperimentalComposeApi::class)
@Composable
private fun ExpandedNavigation(
	component: ProjectSelection
) {
	val slot by component.slot.subscribeAsState()
	Scaffold(
		modifier = Modifier.defaultScaffold(),
		contentWindowInsets = WindowInsets(0, 0, 0, 0),
		content = { scaffoldPadding ->
			val drawerState = rememberDrawerState(DrawerValue.Closed)
			val scope = rememberCoroutineScope()
			PermanentNavigationDrawer(
				modifier = Modifier.rootElement(scaffoldPadding),
				drawerContent = {
					PermanentDrawerSheet(modifier = Modifier.width(Ui.NavDrawer.widthExpanded)) {
						NavigationDrawerContents(component, scope, slot, drawerState)
					}
				},
				content = {
					ProjectSelectionUi(component, Modifier)
				}
			)
		},
		floatingActionButton = {
			ProjectSelectionFab(component)
		}
	)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColumnScope.NavigationDrawerContents(
	component: ProjectSelection,
	scope: CoroutineScope,
	slot: ChildSlot<ProjectSelection.Config, ProjectSelection.Destination>,
	drawerState: DrawerState,
) {
	Spacer(Modifier.height(12.dp))
	ProjectSelection.Locations.values().forEach { item ->
		NavigationDrawerItem(
			icon = { Icon(getLocationIcon(item), contentDescription = item.text.get()) },
			label = { Text(item.name) },
			selected = item == slot.child?.configuration?.location,
			onClick = {
				scope.launch { drawerState.close() }
				component.showLocation(item)
			},
			modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
		)
	}

	Spacer(modifier = Modifier.weight(1f))

	val versionText = remember { getAppVersionString() }

	Text(
		versionText,
		modifier = Modifier
			.padding(Ui.Padding.L)
			.align(Start),
		style = MaterialTheme.typography.labelSmall,
	)
}