package com.darkrockstudios.apps.hammer.base.http

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import net.peanuuutz.tomlkt.Toml
import okio.FileSystem
import okio.IOException
import okio.Path
import kotlin.reflect.KClass

@OptIn(InternalSerializationApi::class)
fun <T : Any> FileSystem.readJson(path: Path, json: Json, clazz: KClass<T>): T? {
	return read(path) {
		val jsonStr = readUtf8()
		json.decodeFromString(clazz.serializer(), jsonStr)
	}
}

// Intentional or-null fallback; deserialization/IO failures map to null.
@Suppress("SwallowedException")
fun <T : Any> FileSystem.readJsonOrNull(path: Path, json: Json, clazz: KClass<T>): T? {
	return try {
		readJson(path, json, clazz)
	} catch (e: SerializationException) {
		null
	} catch (e: IllegalArgumentException) {
		null
	} catch (e: IOException) {
		null
	}
}

inline fun <reified T> FileSystem.readJson(path: Path, json: Json): T? {
	return read(path) {
		val jsonStr = readUtf8()
		json.decodeFromString(jsonStr)
	}
}

inline fun <reified T> FileSystem.readJsonOrNull(path: Path, json: Json): T? {
	return try {
		readJson(path, json)
	} catch (e: SerializationException) {
		null
	} catch (e: IllegalArgumentException) {
		null
	} catch (e: IOException) {
		null
	}
}

inline fun <reified T> FileSystem.writeJson(path: Path, json: Json, obj: T) {
	write(path) {
		val jsonStr = json.encodeToString<T>(obj)
		writeUtf8(jsonStr)
	}
}

@OptIn(InternalSerializationApi::class)
inline fun <reified T : Any> FileSystem.readToml(path: Path, toml: Toml, clazz: KClass<T> = T::class): T {
	return read(path) {
		val tomlStr = readUtf8()
		toml.decodeFromString(clazz.serializer(), tomlStr)
	}
}

/**
 * Reads and decodes [T] from a TOML file, returning null on any read or decode
 * failure rather than throwing. [onError] is invoked with the failure (default
 * no-op) so callers can log site-specific context including the exception.
 *
 * tomlkt does not funnel every decode failure through [SerializationException]:
 * stale or hand-edited files can throw [IllegalArgumentException] (numeric
 * coercion via NumberFormatException, type-mismatch casts, bad booleans) or
 * [IllegalStateException] (parser errors such as a malformed date-time). All are
 * treated as a missing/unusable file.
 */
@Suppress("SwallowedException")
inline fun <reified T : Any> FileSystem.readTomlOrNull(
	path: Path,
	toml: Toml,
	onError: (Throwable) -> Unit = {},
): T? {
	return try {
		readToml<T>(path, toml)
	} catch (e: IOException) {
		onError(e)
		null
	} catch (e: SerializationException) {
		onError(e)
		null
	} catch (e: IllegalArgumentException) {
		onError(e)
		null
	} catch (e: IllegalStateException) {
		onError(e)
		null
	}
}

inline fun <reified T> FileSystem.writeToml(path: Path, toml: Toml, obj: T) {
	write(path) {
		val jsonStr = toml.encodeToString<T>(obj)
		writeUtf8(jsonStr)
	}
}