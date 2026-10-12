package com.darkrockstudios.apps.hammer.plugins.plaintext

import com.darkrockstudios.apps.hammer.common.dependencyinjection.injectIoDispatcher
import com.darkrockstudios.apps.hammer.operations.plugin.PluginSettingsDatasource
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.IOException
import org.koin.core.component.KoinComponent

class PlainTextSettingsStore(private val datasource: PluginSettingsDatasource) : KoinComponent {
	private val ioDispatcher by injectIoDispatcher()
	private val lock = Mutex()

	private val _settings = MutableStateFlow(
		datasource.load(PlainTextPlugin.ID, PlainTextSettings.serializer()) { PlainTextSettings() }
	)
	val settings: StateFlow<PlainTextSettings> = _settings.asStateFlow()

	/** The new settings apply for this session even if saving them fails. */
	suspend fun update(transform: (PlainTextSettings) -> PlainTextSettings) = lock.withLock {
		val updated = _settings.updateAndGet(transform)
		withContext(ioDispatcher) {
			try {
				datasource.store(PlainTextPlugin.ID, PlainTextSettings.serializer(), updated)
			} catch (e: IOException) {
				Napier.e(e) { "Failed to save plain text export settings" }
			}
		}
	}
}
