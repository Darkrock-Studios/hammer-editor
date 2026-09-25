package com.darkrockstudios.apps.hammer.operations.plugin

import com.darkrockstudios.apps.hammer.operations.OperationJson
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import net.peanuuutz.tomlkt.TomlElement
import net.peanuuutz.tomlkt.TomlTable
import okio.IOException
import kotlin.coroutines.CoroutineContext

/** One plugin's declared settings: every declared key always has a valid value. */
class DeclaredSettingsStore internal constructor(
	val pluginId: String,
	val declarations: List<SettingDeclaration>,
	private val datasource: PluginSettingsDatasource,
	private val ioDispatcher: CoroutineContext,
	private val saveScope: CoroutineScope,
) {
	private val saveLock = Mutex()

	private val _values = MutableStateFlow(
		declarations.resolve(datasource.load(pluginId, TomlTable.serializer()) { TomlTable(emptyMap<String, TomlElement>()) }.toSettingValues())
	)
	val values: StateFlow<JsonObject> = _values.asStateFlow()

	/** The current values decoded into a plugin's own settings class, whose properties are the keys. */
	fun <T> decode(serializer: KSerializer<T>): T = OperationJson.decodeFromJsonElement(serializer, values.value)

	/**
	 * Ignores a key that is not declared or a value its declaration rejects. The value applies at once;
	 * saving happens on [saveScope], so it completes even if the caller goes away, and a failed save
	 * leaves the value in effect for the session.
	 */
	fun set(key: String, value: JsonPrimitive) {
		val declaration = declarations.firstOrNull { it.key == key } ?: return
		val accepted = declaration.accept(value) ?: return
		_values.update { JsonObject(it + (key to accepted)) }
		saveScope.launch(ioDispatcher) {
			saveLock.withLock {
				try {
					datasource.store(pluginId, TomlTable.serializer(), values.value.toTomlTable())
				} catch (e: IOException) {
					Napier.e(e) { "Failed to save settings for plugin '$pluginId'" }
				}
			}
		}
	}
}
