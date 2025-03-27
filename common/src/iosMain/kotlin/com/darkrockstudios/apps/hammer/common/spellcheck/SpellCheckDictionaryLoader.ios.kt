package com.darkrockstudios.apps.hammer.common.spellcheck

import com.darkrockstudios.fdic.FrequencyDictionary
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

actual val spellCheckModule = module {
	singleOf(::IosSpellCheckDictionaryLoader) bind SpellCheckDictionaryLoader::class
}

class IosSpellCheckDictionaryLoader : SpellCheckDictionaryLoader() {
	override fun loadDictionary(dictionaryName: String): FrequencyDictionary {
		error("Not Implemented")
	}
}
