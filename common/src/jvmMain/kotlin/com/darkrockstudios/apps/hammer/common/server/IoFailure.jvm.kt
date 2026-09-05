package com.darkrockstudios.apps.hammer.common.server

import java.io.IOException
import java.io.UncheckedIOException

/**
 * `java.net.http.HttpClient`'s constructor wraps a failed `Selector.open()` in an
 * [UncheckedIOException], so a machine that cannot open the JDK's internal loopback socket pair
 * fails with an unchecked exception before a request is ever sent.
 */
actual fun Throwable.asIoFailure(): IOException? = when (this) {
	is IOException -> this
	is UncheckedIOException -> cause as? IOException
	else -> null
}
