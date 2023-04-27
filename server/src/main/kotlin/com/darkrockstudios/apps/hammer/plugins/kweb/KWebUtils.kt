package com.darkrockstudios.apps.hammer.plugins.kweb

import kotlinx.serialization.json.JsonPrimitive
import kweb.*
import kweb.util.json

fun ImageElement.src(path: String): Element {
	return setAttributes(
		Pair("src", JsonPrimitive(path))
	)
}

fun ElementCreator<Element>.rellink(
	rel: LinkRelationship,
	href: String,
	hreflang: String? = null,
	attributes: Map<String, JsonPrimitive> = emptyMap(),
): Element {
	return LinkElement(
		element(
			"link",
			attributes = attributes
				.set("rel", rel.name.json)
				.set("href", href.json)
				.set("hreflang", JsonPrimitive(hreflang))
		)
	)
}
