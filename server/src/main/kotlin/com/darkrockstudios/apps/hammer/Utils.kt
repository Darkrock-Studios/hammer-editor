package com.darkrockstudios.apps.hammer

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import kotlin.reflect.KClass

const val DATA_DIR = "hammer_data"

fun getRootDataDirectory(fileSystem: FileSystem): Path {
	return System.getProperty("user.home").toPath() / DATA_DIR
}

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