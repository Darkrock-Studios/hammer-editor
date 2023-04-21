package com.darkrockstudios.apps.hammer.plugins.kweb

import kotlinx.serialization.json.JsonPrimitive
import kweb.Element
import kweb.ImageElement

fun ImageElement.src(path: String): Element {
	return setAttributes(
		Pair("src", JsonPrimitive(path))
	)
}
