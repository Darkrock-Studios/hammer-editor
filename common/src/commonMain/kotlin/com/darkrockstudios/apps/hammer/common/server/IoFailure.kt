package com.darkrockstudios.apps.hammer.common.server

import okio.IOException

/**
 * The [IOException] this throwable is, or the one a platform hides behind an unchecked wrapper,
 * or null when it is not an IO failure.
 */
expect fun Throwable.asIoFailure(): IOException?
