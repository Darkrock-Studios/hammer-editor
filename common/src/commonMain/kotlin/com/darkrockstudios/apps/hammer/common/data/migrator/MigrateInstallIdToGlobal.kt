package com.darkrockstudios.apps.hammer.common.data.migrator

import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import io.github.aakira.napier.Napier
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path.Companion.toPath

/**
 * One-shot migration that moves the per-install id out of `server.json`
 * (where it used to live as a field on `ServerSettings`) and into
 * `GlobalSettings`. Originally the install id only existed when sync was
 * configured and was regenerated on every server reconfig; we want it as
 * a single stable identity used both for server auth and for the
 * writing-activity device id, so it lives on `GlobalSettings` now.
 *
 * The production `ServerSettings` no longer has the field — we read the
 * legacy file shape via [ServerSettingsOld] just for the copy-over so the
 * production class stays at its final form. Idempotent: if the global
 * slot is already populated this is a no-op. Safe to delete once enough
 * time has passed that any user upgrading would already have run it.
 */
class MigrateInstallIdToGlobal(
	private val globalSettingsStore: GlobalSettingsStore,
	private val fileSystem: FileSystem,
	private val json: Json,
) : GlobalMigration {

	// Best-effort migration; any read failure logged and treated as no legacy id.
	@Suppress("TooGenericExceptionCaught")
	override suspend fun migrate() {
		if (globalSettingsStore.globalSettings.installId != null) return

		val projectsDir = globalSettingsStore.globalSettings.projectsDirectory.toPath()
		val legacyPath = projectsDir / LEGACY_SERVER_FILE_NAME
		if (!fileSystem.exists(legacyPath)) return

		val legacyInstallId = try {
			val text = fileSystem.read(legacyPath) { readUtf8() }
			json.decodeFromString<ServerSettingsOld>(text).installId
		} catch (e: Exception) {
			Napier.w("Failed to read legacy server.json for installId migration", e)
			null
		} ?: return

		Napier.i("Migrating installId from server.json to GlobalSettings")
		globalSettingsStore.updateSettings { it.copy(installId = legacyInstallId) }
	}

	@Serializable
	private data class ServerSettingsOld(
		val installId: String? = null,
	)

	companion object {
		private const val LEGACY_SERVER_FILE_NAME = "server.json"
	}
}
