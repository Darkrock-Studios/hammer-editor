package com.darkrockstudios.apps.hammer.common.spellcheck

import com.darkrockstudios.fdic.FrequencyDictionary
import org.koin.core.module.Module

expect val spellCheckModule: Module

abstract class SpellCheckDictionaryLoader {
	abstract fun loadDictionary(dictionaryName: String): FrequencyDictionary
}