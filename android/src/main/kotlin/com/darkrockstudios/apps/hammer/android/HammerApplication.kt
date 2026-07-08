package com.darkrockstudios.apps.hammer.android

import android.app.Application
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN
import android.content.ComponentName
import android.os.Build
import androidx.collection.intSetOf
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import coil3.ImageLoader
import coil3.SingletonImageLoader
import com.darkrockstudios.apps.hammer.android.aboutlibraries.aboutLibrariesModule
import com.darkrockstudios.apps.hammer.android.shortcuts.ProjectShortcutsManager
import com.darkrockstudios.apps.hammer.android.shortcuts.shortcutsModule
import com.darkrockstudios.apps.hammer.android.widgets.AddNoteWidgetReceiver
import com.darkrockstudios.apps.hammer.android.widgets.StoriesListWidgetReceiver
import com.darkrockstudios.apps.hammer.android.widgets.StoryInfoWidgetReceiver
import com.darkrockstudios.apps.hammer.common.BuildConfig
import com.darkrockstudios.apps.hammer.common.data.migrator.DataMigrator
import com.darkrockstudios.apps.hammer.common.logStartupBanner
import com.darkrockstudios.apps.hammer.common.dependencyinjection.NapierLogger
import com.darkrockstudios.apps.hammer.common.dependencyinjection.appModule
import com.darkrockstudios.apps.hammer.common.dependencyinjection.imageLoadingModule
import com.darkrockstudios.apps.hammer.common.dependencyinjection.mainModule
import com.darkrockstudios.apps.hammer.common.setExternalDirectories
import com.darkrockstudios.apps.hammer.common.setInternalDirectories
import com.darkrockstudios.apps.hammer.common.util.AndroidSettingsKeys
import com.russhwolf.settings.Settings
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext.startKoin
import org.koin.java.KoinJavaComponent
import org.koin.java.KoinJavaComponent.getKoin

class HammerApplication : Application(), SingletonImageLoader.Factory {

	private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

	override fun onCreate() {
		super.onCreate()

		initializeDirectories()
		Napier.base(FileLogger(scope = applicationScope))
		logStartupBanner()

		startKoin {
			logger(NapierLogger())
			androidContext(this@HammerApplication)
			modules(
				mainModule,
				imageLoadingModule,
				aboutLibrariesModule,
				shortcutsModule,
				appModule(applicationScope)
			)
		}

		runBlocking { getKoin().get<DataMigrator>(DataMigrator::class).handleDataMigration() }

		applicationScope.launch {
			getKoin().get<ProjectShortcutsManager>().refresh()
		}

		publishWidgetPreviews()
	}

	private fun publishWidgetPreviews() {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return
		applicationScope.launch {
			val appWidgetManager = getSystemService(AppWidgetManager::class.java) ?: return@launch
			val glanceManager = GlanceAppWidgetManager(this@HammerApplication)
			val receivers = listOf(
				AddNoteWidgetReceiver::class,
				StoriesListWidgetReceiver::class,
				StoryInfoWidgetReceiver::class,
			)
			receivers
				.filter { needsPublish(appWidgetManager, it.java) }
				.forEach { receiver ->
					runCatching {
						glanceManager.setWidgetPreviews(receiver, intSetOf(WIDGET_CATEGORY_HOME_SCREEN))
					}.onFailure { Napier.w(it) { "Failed to publish previews for $receiver" } }
				}
		}
	}

	@androidx.annotation.RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
	private fun needsPublish(
		appWidgetManager: AppWidgetManager,
		receiverClass: Class<out GlanceAppWidgetReceiver>,
	): Boolean {
		val componentName = ComponentName(this, receiverClass)
		val info = appWidgetManager.installedProviders.firstOrNull { it.provider == componentName }
			?: return true
		return (info.generatedPreviewCategories and WIDGET_CATEGORY_HOME_SCREEN) == 0
	}

	override fun newImageLoader(context: coil3.PlatformContext): ImageLoader {
		return KoinJavaComponent.getKoin().get()
	}

	private fun initializeDirectories() {
		// Public (external) storage is only available on F-Droid builds. On any other
		// build, force internal storage so a stale "use internal = false" preference can
		// never send us to a directory we have no permission for.
		val useInternalData = !BuildConfig.FDROID || Settings().getBoolean(
			AndroidSettingsKeys.KEY_USE_INTERNAL_STORAGE,
			true
		)
		if (useInternalData) {
			setInternalDirectories(this)
		} else {
			setExternalDirectories(this)
		}
	}

	override fun onTerminate() {
		super.onTerminate()
		applicationScope.cancel("Application onTerminate")
	}
}
