package com.darkrockstudios.apps.hammer.common.server

import okio.IOException

actual fun Throwable.asIoFailure(): IOException? = this as? IOException
