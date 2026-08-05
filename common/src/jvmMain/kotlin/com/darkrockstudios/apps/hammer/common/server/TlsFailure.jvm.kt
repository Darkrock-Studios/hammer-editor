package com.darkrockstudios.apps.hammer.common.server

import javax.net.ssl.SSLException

actual fun Throwable.isTlsFailure(): Boolean = causeChain().any { it is SSLException }
