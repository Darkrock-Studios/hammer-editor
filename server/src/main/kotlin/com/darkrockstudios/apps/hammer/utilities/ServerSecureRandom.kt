package com.darkrockstudios.apps.hammer.utilities

import java.security.SecureRandom

/**
 * The server's CSPRNG.
 *
 * Thar be dragons: never build this with [SecureRandom.getInstanceStrong]. On Linux it
 * resolves to NativePRNGBlocking, which reads `/dev/random`; on a pre-5.6 kernel with a
 * shallow entropy pool (NAS boxes, small VMs) that read blocks until the pool refills,
 * stalling every caller with nothing thrown and nothing logged. Sync-ID generation draws
 * 30 ints per session, so `begin_sync` is the first thing to hang. `DRBG` is no better:
 * it seeds from the same source and blocks the same way.
 */
fun nonBlockingSecureRandom(): SecureRandom = SecureRandom()
