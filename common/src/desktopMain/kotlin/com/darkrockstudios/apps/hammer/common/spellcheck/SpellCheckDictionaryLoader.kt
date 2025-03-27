package com.darkrockstudios.apps.hammer.common.spellcheck

import com.darkrockstudios.fdic.FrequencyDictionary
import com.darkrockstudios.fdic.FrequencyDictionaryIO
import okio.source
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module
import java.io.InputStream

actual val spellCheckModule = module {
	singleOf(::DestopSpellCheckDictionaryLoader) bind SpellCheckDictionaryLoader::class
}

class DestopSpellCheckDictionaryLoader : SpellCheckDictionaryLoader() {
	override fun loadDictionary(dictionaryName: String): FrequencyDictionary {
		val path = "/raw/$dictionaryName"
		this::class.java.getResourceAsStream(path).use { inputStream: InputStream? ->
			return if (inputStream != null) {
				FrequencyDictionaryIO.readFdic(inputStream.source())
			} else {
				error("Failed to dictionary file: $dictionaryName")
			}
		}
	}
}
