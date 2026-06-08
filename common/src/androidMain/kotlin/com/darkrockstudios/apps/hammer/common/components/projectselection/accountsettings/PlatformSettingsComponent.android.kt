package com.darkrockstudios.apps.hammer.common.components.projectselection.accountsettings

import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
import android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS
import androidx.core.content.ContextCompat.startActivity
import androidx.core.net.toUri
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.getAndUpdate
import com.darkrockstudios.apps.hammer.common.components.SavableComponent
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.migrator.DataMigrator
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.dependencyinjection.injectIoDispatcher
import com.darkrockstudios.apps.hammer.common.dependencyinjection.injectMainDispatcher
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.moveDirectory
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import com.darkrockstudios.apps.hammer.common.setExternalDirectories
import com.darkrockstudios.apps.hammer.common.setInternalDirectories
import com.darkrockstudios.apps.hammer.common.util.AndroidSettingsKeys
import com.russhwolf.settings.Settings
import com.russhwolf.settings.set
import dev.icerock.moko.permissions.*
import dev.icerock.moko.permissions.storage.STORAGE
import dev.icerock.moko.permissions.storage.WRITE_STORAGE
import io.github.aakira.napier.Napier
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okio.FileSystem
import okio.IOException
import okio.Path.Companion.toPath
import org.koin.core.component.get
import org.koin.core.component.inject

class AndroidPlatformSettingsComponent(
	componentContext: ComponentContext,
	private val context: Context,
	private val fileSystem: FileSystem,
) :
	PlatformSettings,
	SavableComponent<AndroidPlatformSettingsComponent.PlatformState>(componentContext) {

	private val mainDispatcher by injectMainDispatcher()
	private val ioDispatcher by injectIoDispatcher()

	private val globalSettingsStore: GlobalSettingsStore by inject()
	private val projectsRepository: ProjectsRepository by inject()

	val permissionsController = PermissionsController(context)

	private val _state = MutableValue(
		PlatformState(projectsDir = projectsRepository.getProjectsDirectory())
	)
	override val state: Value<PlatformState> = _state
	override fun getStateSerializer() = PlatformState.serializer()

	private val settings: Settings by inject()

	init {
		scope.launch {
			val screenOn = settings.getBoolean(AndroidSettingsKeys.KEY_SCREEN_ON, false)
			// Derive the toggle from where projects actually live rather than trusting the
			// stored flag alone, so the UI can't desync from reality. Heal the stored flag
			// (read at startup by HammerApplication) if the two disagree.
			val internalStorage = !isProjectsDirExternal()
			if (settings.getBoolean(AndroidSettingsKeys.KEY_USE_INTERNAL_STORAGE, true) != internalStorage) {
				settings[AndroidSettingsKeys.KEY_USE_INTERNAL_STORAGE] = internalStorage
			}
			val externalStorageAccess = isExternalStorageGranted()
			val dndSelected = globalSettingsStore.globalSettings.enableDndInFocusMode
			val dndGranted = isNotificationPolicyGranted()

			_state.getAndUpdate {
				it.copy(
					keepScreenOn = screenOn,
					dataStorageInternal = internalStorage,
					fileAccessGranted = externalStorageAccess,
					enableDndInFocusMode = dndSelected,
					dndPermissionGranted = dndGranted,
				)
			}
		}
	}

	private suspend fun isExternalStorageGranted(): Boolean {
		return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
			Environment.isExternalStorageManager()
		} else {
			val write = permissionsController.getPermissionState(Permission.WRITE_STORAGE)
			val read = permissionsController.getPermissionState(Permission.STORAGE)
			read == PermissionState.Granted && write == PermissionState.Granted
		}
	}

	override fun onResume() {
		super.onResume()

		// TODO This is not initialized yet... why?!
		scope.launch {
			if (_state != null) {
				val externalStorageAccess = isExternalStorageGranted()
				val dndGranted = isNotificationPolicyGranted()
				_state.getAndUpdate {
					it.copy(
						fileAccessGranted = externalStorageAccess,
						dndPermissionGranted = dndGranted,
					)
				}
			}
		}
	}

	fun updateKeepScreenOn(keepOn: Boolean) {
		settings[AndroidSettingsKeys.KEY_SCREEN_ON] = keepOn

		_state.getAndUpdate {
			it.copy(
				keepScreenOn = keepOn
			)
		}
	}

	fun updateEnableDndInFocusMode(enabled: Boolean) {
		scope.launch {
			globalSettingsStore.updateSettings {
				it.copy(enableDndInFocusMode = enabled)
			}
			_state.getAndUpdate {
				it.copy(enableDndInFocusMode = enabled)
			}
		}
	}

	fun promptForFileAccess() {
		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
			val intent = Intent(ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
				flags += Intent.FLAG_ACTIVITY_NEW_TASK
				data = ("package:" + context.packageName).toUri()
			}

			startActivity(context, intent, null)
		} else {
			scope.launch {
				try {
					permissionsController.providePermission(Permission.WRITE_STORAGE)
					permissionsController.providePermission(Permission.STORAGE)

					val writeState =
						permissionsController.getPermissionState(Permission.WRITE_STORAGE)
					val readState = permissionsController.getPermissionState(Permission.STORAGE)

					if (writeState != PermissionState.Granted || readState != PermissionState.Granted) {
						Napier.w("External Storage permissions were not successfully granted")
					} else {
						Napier.i("External Storage permissions have been granted successfully.")
					}
				} catch (deniedAlways: DeniedAlwaysException) {
					Napier.w("External Storage permission always denied", deniedAlways)
				} catch (denied: DeniedException) {
					Napier.w("External Storage permission denied", denied)
				}
			}
		}
	}

	/**
	 * Whether the current projects directory lives under public (external) storage. Used to
	 * keep the storage toggle in sync with the real location. Reads only the path string, so
	 * it's safe to call without storage permission.
	 */
	private fun isProjectsDirExternal(): Boolean {
		val externalRoot = Environment
			.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
			.absolutePath
		return globalSettingsStore.globalSettings.projectsDirectory.startsWith(externalRoot)
	}

	fun setExternalStorage() = setStorage(internal = false)
	fun setInternalStorage() = setStorage(internal = true)

	private fun setStorage(internal: Boolean) {
		scope.launch {
			val oldPath = globalSettingsStore.globalSettings.projectsDirectory.toPath()
			if (internal) setInternalDirectories(context) else setExternalDirectories(context)
			val newPath = globalSettingsStore.defaultProjectDir()
			val newOkioPath = newPath.toOkioPath()

			// moveDirectory no-ops when the source and destination are the same directory or
			// the source is missing, and tolerates entries vanishing mid-iteration.
			try {
				withContext(ioDispatcher) {
					fileSystem.moveDirectory(source = oldPath, destination = newOkioPath)
				}
			} catch (e: IOException) {
				Napier.e("Failed to move projects from $oldPath to $newOkioPath", e)
			}

			settings[AndroidSettingsKeys.KEY_USE_INTERNAL_STORAGE] = internal

			setProjectsDir(newPath.path)
			withContext(mainDispatcher) {
				_state.getAndUpdate {
					it.copy(
						dataStorageInternal = internal
					)
				}
			}
		}
	}

	fun setProjectsDir(path: String) {
		val hpath = HPath(
			path = path,
			name = "",
			isAbsolute = true
		)

		scope.launch {
			globalSettingsStore.updateSettings {
				it.copy(
					projectsDirectory = path
				)
			}

			projectsRepository.ensureProjectDirectory()

			// Migrate the new project directory if needed
			val dataMigrator: DataMigrator = get<DataMigrator>()
			dataMigrator.handleDataMigration()

			withContext(mainDispatcher) {
				_state.getAndUpdate {
					it.copy(projectsDir = hpath)
				}
			}
		}
	}

	fun isNotificationPolicyGranted(): Boolean {
		val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
		return nm.isNotificationPolicyAccessGranted
	}

	fun launchNotificationPolicyPermissionScreen(activity: Activity) {
		val intent = Intent(ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
		activity.startActivity(intent)
	}

	@Serializable
	data class PlatformState(
		val keepScreenOn: Boolean = false,
		val dataStorageInternal: Boolean = true,
		val fileAccessGranted: Boolean = false,
		val enableDndInFocusMode: Boolean = false,
		val dndPermissionGranted: Boolean = false,
		val projectsDir: HPath,
	)
}