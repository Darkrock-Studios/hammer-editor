package com.darkrockstudios.apps.hammer.utilities

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.security.cert.X509Certificate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DevSelfSignedCertTest {

	@TempDir
	lateinit var tempDir: File

	@Test
	fun `generates a keystore holding the server key entry`() {
		val file = File(tempDir, "dev.jks")

		val dev = DevSelfSignedCert.getOrCreate(file)

		assertTrue(file.exists(), "keystore should be persisted to disk")
		assertTrue(dev.keyStore.isKeyEntry(dev.alias), "keystore should hold the server key entry")
		val cert = dev.keyStore.getCertificate(dev.alias) as X509Certificate
		assertNotNull(cert)
		// A self-signed cert's issuer equals its subject.
		assertEquals(cert.subjectX500Principal, cert.issuerX500Principal)
	}

	@Test
	fun `reuses the persisted keystore instead of regenerating`() {
		val file = File(tempDir, "dev.jks")

		val first = DevSelfSignedCert.getOrCreate(file)
		val second = DevSelfSignedCert.getOrCreate(file)

		val firstCert = first.keyStore.getCertificate(first.alias) as X509Certificate
		val secondCert = second.keyStore.getCertificate(second.alias) as X509Certificate
		// Same persisted cert on the second call — the fingerprint stays stable across restarts.
		assertEquals(firstCert, secondCert)
	}
}
