package com.darkrockstudios.apps.hammer.common.data.migrator

/**
 * Migration that touches global state (e.g. files in the config directory),
 * not a single project. Run once at app startup before any per-project
 * migrations. Implementations must be idempotent: they should self-gate on
 * the precondition they need to fix, so repeated runs after a successful
 * migration do nothing.
 */
interface GlobalMigration {
	suspend fun migrate()
}
