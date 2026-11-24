package com.darkrockstudios.apps.hammer.common.data.nux

import com.darkrockstudios.apps.hammer.common.getConfigDirectory
import io.github.aakira.napier.Napier
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import net.peanuuutz.tomlkt.Toml
import okio.FileSystem
import okio.Path.Companion.toPath

interface NuxDatasource {
	fun load(): NuxState
	fun store(state: NuxState)
}

class NuxFilesystemDatasource(
	private val fileSystem: FileSystem,
	private val toml: Toml,
) : NuxDatasource {

	override fun load(): NuxState {
		if (!fileSystem.exists(STATE_PATH)) return NuxState()

		val text = fileSystem.read(STATE_PATH) { readUtf8() }
		return try {
			toml.decodeFromString(text)
		} catch (e: IllegalArgumentException) {
			Napier.e("Failed to load NUX state, starting over.", e)
			NuxState()
		}
	}

	override fun store(state: NuxState) {
		fileSystem.createDirectories(STATE_PATH.parent!!)
		fileSystem.write(STATE_PATH, false) {
			writeUtf8(toml.encodeToString(state))
		}
	}

	companion object {
		private const val FILE_NAME = "nux.toml"
		val STATE_PATH = getConfigDirectory().toPath() / FILE_NAME
	}
}
