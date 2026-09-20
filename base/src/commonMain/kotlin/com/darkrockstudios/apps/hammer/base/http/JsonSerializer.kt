package com.darkrockstudios.apps.hammer.base.http

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import org.koin.core.qualifier.named

/** For human-facing files on disk: indented, and forgiving of hand edits. */
@OptIn(ExperimentalSerializationApi::class)
fun createJsonSerializer(): Json {
	return Json {
		prettyPrint = true
		prettyPrintIndent = "\t"
		encodeDefaults = true
		coerceInputValues = true
		allowTrailingComma = true
		ignoreUnknownKeys = true
	}
}

/**
 * For machine-facing content on the wire. Matches Ktor's `DefaultJson` plus `ignoreUnknownKeys`,
 * which the sync protocol depends on: synced models gain fields without a protocol bump, so a peer
 * must drop fields a newer build wrote rather than failing the decode. See `SYNCING-PROTOCOL.md`.
 */
fun createNetworkJsonSerializer(): Json {
	return Json {
		encodeDefaults = true
		isLenient = true
		allowSpecialFloatingPointValues = true
		allowStructuredMapKeys = true
		ignoreUnknownKeys = true
	}
}

/** Resolves [createNetworkJsonSerializer]; an unqualified `Json` is the file serializer. */
val NetworkJsonQualifier = named("networkJson")
