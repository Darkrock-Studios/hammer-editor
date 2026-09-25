package com.darkrockstudios.apps.hammer.operations.plugin

import kotlinx.coroutines.CoroutineScope
import org.koin.core.module.Module

/**
 * An in-process extension to the client. Registering it in [installedPlugins] (or a platform's
 * own registration file) is what activates it; there is no separate enablement switch.
 *
 * Hooks run synchronously on the caller's thread, which is often the UI thread during startup or a
 * project open. Keep them quick and launch real work into the scope they are given.
 */
interface ClientPlugin {
	/** Stable, lowercase, directory-safe. Keys this plugin's settings file and project data directory. */
	val id: String

	/** Installed alongside the app's own modules. */
	fun koinModule(): Module? = null

	/** Runs once Koin is up and data migration has finished. Must not assume a UI. */
	fun onAppStart(appScope: CoroutineScope) {}

	/** A project was opened for editing. Background work such as sync does not trigger this. */
	fun onProjectOpened(project: ProjectPluginContext) {}

	/** The project's coroutine scope is cancelled, and briefly awaited, after this returns. */
	fun onProjectClosed(project: ProjectPluginContext) {}
}
