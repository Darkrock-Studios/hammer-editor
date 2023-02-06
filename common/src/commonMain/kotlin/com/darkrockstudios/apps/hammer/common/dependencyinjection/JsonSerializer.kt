package com.darkrockstudios.apps.hammer.common.dependencyinjection

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json

@OptIn(ExperimentalSerializationApi::class)
fun createJsonSerializer(): Json {
	return Json {
		prettyPrint = true
		prettyPrintIndent = "\t"
	}
}