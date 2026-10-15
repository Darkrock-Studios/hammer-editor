package com.darkrockstudios.apps.hammer.common.data

import com.darkrockstudios.apps.hammer.common.fileio.HPath

/**
 * Where the user's projects live. Account-level Data Sources take this where a project-scoped
 * one would take a [ProjectDef], so they don't reach up to the settings store. Read it on every
 * use: the user can change the directory while the app is running.
 */
fun interface ProjectsDirectory {
	fun current(): HPath
}
