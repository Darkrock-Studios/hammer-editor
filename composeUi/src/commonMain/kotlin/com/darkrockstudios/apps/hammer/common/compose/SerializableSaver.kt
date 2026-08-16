package com.darkrockstudios.apps.hammer.common.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import kotlinx.serialization.json.Json

/**
 * Creates a [Saver] for any kotlinx.serialization @Serializable type.
 * This allows using rememberSaveable with custom data classes.
 *
 * Usage:
 * ```
 * var myState by rememberSaveable(stateSaver = serializableSaver<MyClass>()) {
 *     mutableStateOf<MyClass?>(null)
 * }
 * ```
 */
inline fun <reified T : Any> serializableSaver(): Saver<T?, String> = Saver(
	save = { value -> value?.let { Json.encodeToString(it) } },
	restore = { json -> Json.decodeFromString(json) }
)

/**
 * Creates a [Saver] for a non-nullable kotlinx.serialization @Serializable type.
 *
 * Usage:
 * ```
 * var myState by rememberSaveable(stateSaver = serializableSaverNonNull<MyClass>()) {
 *     mutableStateOf(MyClass(...))
 * }
 * ```
 */
inline fun <reified T : Any> serializableSaverNonNull(): Saver<T, String> = Saver(
	save = { value -> Json.encodeToString(value) },
	restore = { json -> Json.decodeFromString(json) }
)

/**
 * A string list that survives a configuration change. Use it for draft collections (tags, chips,
 * selections) that a plain [mutableStateListOf] would drop when the composition is rebuilt.
 */
@Composable
fun rememberSaveableStringList(): SnapshotStateList<String> = rememberSaveable(
	saver = listSaver<SnapshotStateList<String>, String>(
		save = { it.toList() },
		restore = { it.toMutableStateList() },
	)
) { mutableStateListOf() }
