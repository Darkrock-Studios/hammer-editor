package com.darkrockstudios.apps.hammer.common.data

import org.koin.core.scope.Scope

/**
 * Every instance bound in Koin is told when a project is opened for editing and when that editing
 * session closes. Temporary scopes (background sync, import) are not reported.
 */
interface ProjectLifecycleListener {
	fun onProjectOpened(projectDef: ProjectDef, projectScope: Scope)
	fun onProjectClosed(projectDef: ProjectDef)
}
