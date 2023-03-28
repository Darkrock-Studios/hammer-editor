package com.darkrockstudios.apps.hammer

import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath

const val DATA_DIR = "hammer_data"

fun getRootDataDirectory(fileSystem: FileSystem): Path {
    return System.getProperty("user.home").toPath() / DATA_DIR
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