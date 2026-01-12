package com.darkrockstudios.apps.hammer.common.compose

import androidx.compose.runtime.saveable.Saver
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
