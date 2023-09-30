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

inline fun <reified T> FileSystem.writeToml(path: Path, toml: Toml, obj: T) {
	write(path) {
		val jsonStr = toml.encodeToString<T>(obj)
		writeUtf8(jsonStr)
	}
}