package utils

import com.darkrockstudios.apps.hammer.common.data.ProjectsDirectory
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import okio.Path.Companion.toPath

/** Follows [store]'s current setting, as the production binding does. */
fun projectsDirectoryOf(store: GlobalSettingsStore) =
	ProjectsDirectory { store.globalSettings.projectsDirectory.toPath().toHPath() }
