package com.darkrockstudios.apps.hammer.plugins.kweb

import com.github.aymanizz.ktori18n.DEFAULT_RESOURCE_BUNDLE
import com.github.aymanizz.ktori18n.R
import kotlinx.serialization.json.JsonPrimitive
import kweb.*
import kweb.util.json
import org.jetbrains.annotations.PropertyKey

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

fun Element.text(@PropertyKey(resourceBundle = DEFAULT_RESOURCE_BUNDLE) key: String, loc: KwebLocalizer): Element {
	return text(loc.t(R(key)))
}