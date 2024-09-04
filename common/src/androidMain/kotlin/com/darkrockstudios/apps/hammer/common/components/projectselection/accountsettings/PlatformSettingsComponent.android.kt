package com.darkrockstudios.apps.hammer.common.components.projectselection.accountsettings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
import androidx.core.content.ContextCompat.startActivity
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.getAndUpdate
import com.darkrockstudios.apps.hammer.common.components.SavableComponent
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsRepository
import com.darkrockstudios.apps.hammer.common.data.migrator.DataMigrator
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.dependencyinjection.injectMainDispatcher
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import com.darkrockstudios.apps.hammer.common.setExternalDirectories
import com.darkrockstudios.apps.hammer.common.setInternalDirectories
import com.darkrockstudios.apps.hammer.common.util.AndroidSettingsKeys
import com.russhwolf.settings.Settings
import com.russhwolf.settings.boolean
import com.russhwolf.settings.set
import dev.icerock.moko.permissions.DeniedAlwaysException
import dev.icerock.moko.permissions.DeniedException
import dev.icerock.moko.permissions.Permission
import dev.icerock.moko.permissions.PermissionState
import dev.icerock.moko.permissions.PermissionsController
import io.github.aakira.napier.Napier
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okio.FileSystem
import okio.Path
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

	private val globalSettingsRepository: GlobalSettingsRepository by inject()
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
			val internalStorage =
				settings.getBoolean(AndroidSettingsKeys.KEY_USE_INTERNAL_STORAGE, true)
			val externalStorageAccess = isExternalStorageGranted()
			_state.getAndUpdate {
				it.copy(
					keepScreenOn = screenOn,
					dataStorageInternal = internalStorage,
					fileAccessGranted = externalStorageAccess,
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
				_state.getAndUpdate {
					it.copy(
						fileAccessGranted = externalStorageAccess
					)
				}
			}
		}
	}

	fun updateKeepScreenOn(keepOn: Boolean) {
		settings.boolean(AndroidSettingsKeys.KEY_SCREEN_ON, keepOn)

		_state.getAndUpdate {
			it.copy(
				keepScreenOn = keepOn
			)
		}
	}

	fun promptForFileAccess() {
		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
			val intent = Intent(ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
				flags += Intent.FLAG_ACTIVITY_NEW_TASK
				data = Uri.parse("package:" + context.packageName)
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

	fun setExternalStorage() = setStorage(internal = false)
	fun setInternalStorage() = setStorage(internal = true)

	private fun setStorage(internal: Boolean) {
		val oldPath = globalSettingsRepository.globalSettings.projectsDirectory.toPath()
		if (internal) setInternalDirectories(context) else setExternalDirectories(context)
		val newPath = globalSettingsRepository.defaultProjectDir()
		moveProjectDirectory(oldPath = oldPath, newPath = newPath.toOkioPath())

		settings[AndroidSettingsKeys.KEY_USE_INTERNAL_STORAGE] = internal

		setProjectsDir(newPath.path)
		_state.getAndUpdate {
			it.copy(
				dataStorageInternal = internal
			)
		}
	}

	private fun moveProjectDirectory(oldPath: Path, newPath: Path) {
		moveFilesRecursively(oldPath, newPath, fileSystem)
		fileSystem.deleteRecursively(oldPath)
	}

	private fun moveFilesRecursively(
		sourceDir: Path,
		destinationDir: Path,
		fileSystem: FileSystem,
	) {
		if (!fileSystem.exists(destinationDir)) {
			fileSystem.createDirectories(destinationDir)
		}

		fileSystem.list(sourceDir).forEach { sourcePath ->
			val destinationPath = destinationDir / sourcePath.name
			if (fileSystem.metadata(sourcePath).isDirectory) {
				moveFilesRecursively(sourcePath, destinationPath, fileSystem)
				fileSystem.delete(sourcePath)
			} else {
				fileSystem.copy(sourcePath, destinationPath)
				fileSystem.delete(sourcePath)
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
			globalSettingsRepository.updateSettings {
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

	@Serializable
	data class PlatformState(
		val keepScreenOn: Boolean = false,
		val dataStorageInternal: Boolean = true,
		val fileAccessGranted: Boolean = false,
		val projectsDir: HPath,
	)
}