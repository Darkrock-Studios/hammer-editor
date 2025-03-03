package com.darkrockstudios.apps.hammer.common.spellcheck

import android.content.Context
import com.darkrockstudios.fdic.FrequencyDictionary
import com.darkrockstudios.fdic.FrequencyDictionaryIO
import okio.source
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

actual val spellCheckModule = module {
	singleOf(::AndroidSpellCheckDictionaryLoader) bind SpellCheckDictionaryLoader::class
}

class AndroidSpellCheckDictionaryLoader(
	private val context: Context,
) : SpellCheckDictionaryLoader() {

	override fun loadDictionary(dictionaryName: String): FrequencyDictionary {
		val resourceId = context.resources.getIdentifier(
			dictionaryName,
			"raw",
			context.packageName
		)
		return FrequencyDictionaryIO.readFdic(
			context.resources.openRawResource(resourceId).source()
		)
	}
}