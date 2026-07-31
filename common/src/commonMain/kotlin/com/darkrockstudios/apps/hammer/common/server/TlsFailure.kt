package com.darkrockstudios.apps.hammer.common.server

/**
 * True when this throwable, or anything it wraps, is a TLS handshake or certificate failure.
 *
 * Clients only ever speak HTTPS, so this is what a server reachable only over plain HTTP looks
 * like from the client side, and it needs a different message than a generic network error.
 */
expect fun Throwable.isTlsFailure(): Boolean

/** Walks the `cause` chain, bounded so a self-referential or cyclic cause cannot spin forever. */
internal fun Throwable.causeChain(): Sequence<Throwable> =
	generateSequence(this) { throwable -> throwable.cause?.takeIf { it !== throwable } }.take(16)
