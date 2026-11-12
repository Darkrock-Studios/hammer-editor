package com.darkrockstudios.apps.hammer.utilities

import java.security.SecureRandom

/**
 * The server's CSPRNG, for everything a request can reach.
 *
 * Thar be dragons: never build this with [SecureRandom.getInstanceStrong]. On Linux it
 * resolves to NativePRNGBlocking, which reads `/dev/random`; on a pre-5.6 kernel with a
 * shallow entropy pool (NAS boxes, small VMs) that read blocks until the pool refills,
 * stalling every caller with nothing thrown and nothing logged. Sync-ID generation draws
 * 30 ints per session, so `begin_sync` is the first thing to hang.
 *
 * Provider order is not a safe default here either: it yields NativePRNG on Linux but
 * DRBG on Windows, and on Linux DRBG seeds from `/dev/random` and blocks just the same.
 * So pin the `/dev/urandom`-only variant where it exists, and fall back to the platform
 * default only on hosts that have no `/dev/random` to block on.
 */
fun nonBlockingSecureRandom(): SecureRandom =
	runCatching { SecureRandom.getInstance(NON_BLOCKING_ALGORITHM) }.getOrElse { SecureRandom() }

/** Reads `/dev/urandom` for both seed and output. Present on Linux, absent elsewhere. */
const val NON_BLOCKING_ALGORITHM = "NativePRNGNonBlocking"

/**
 * For minting long-lived key material, and only from one-shot CLI commands.
 *
 * This one blocks on `/dev/random`, which is the point: on a freshly provisioned host
 * `/dev/urandom` will happily return output from an uninitialized pool with no error, and
 * a master key must never be drawn from that. Blocking until the pool is seeded is the
 * correct trade for a key that outlives every session.
 *
 * Keep this off every request path. The same block that is correct here is the
 * [nonBlockingSecureRandom] hazard described above.
 */
fun keyMintingSecureRandom(): SecureRandom = SecureRandom.getInstanceStrong()
