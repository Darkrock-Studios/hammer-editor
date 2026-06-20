package com.darkrockstudios.apps.hammer.utilities

import com.darkrockstudios.apps.hammer.utils.TestCerts
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PemUtilsTest {

	@TempDir
	lateinit var tempDir: File

	@Test
	fun `loads a PEM cert chain and key into a keystore`() {
		val (keyPair, cert) = TestCerts.selfSigned()
		val certChain = File(tempDir, "fullchain.pem").also { TestCerts.writePem(it, cert) }
		val privateKey =
			File(tempDir, "privkey.pem").also { TestCerts.writePem(it, keyPair.private) }

		val keyStore = loadPemAsKeyStore(certChain.path, privateKey.path)

		assertTrue(keyStore.containsAlias("server"))
		assertNotNull(keyStore.getKey("server", CharArray(0)))
		val chain = keyStore.getCertificateChain("server")
		assertEquals(1, chain.size)
		assertEquals(cert, chain[0])
	}

	@Test
	fun `missing cert chain file throws`() {
		val (keyPair, _) = TestCerts.selfSigned()
		val privateKey =
			File(tempDir, "privkey.pem").also { TestCerts.writePem(it, keyPair.private) }

		assertFailsWith<IllegalArgumentException> {
			loadPemAsKeyStore(File(tempDir, "absent.pem").path, privateKey.path)
		}
	}

	@Test
	fun `missing private key file throws`() {
		val (_, cert) = TestCerts.selfSigned()
		val certChain = File(tempDir, "fullchain.pem").also { TestCerts.writePem(it, cert) }

		assertFailsWith<IllegalArgumentException> {
			loadPemAsKeyStore(certChain.path, File(tempDir, "absent.pem").path)
		}
	}

	@Test
	fun `key file containing no private key throws`() {
		val (_, cert) = TestCerts.selfSigned()
		val certChain = File(tempDir, "fullchain.pem").also { TestCerts.writePem(it, cert) }
		// A file that parses but holds a certificate, not a key.
		val notAKey = File(tempDir, "notakey.pem").also { TestCerts.writePem(it, cert) }

		assertFailsWith<IllegalArgumentException> {
			loadPemAsKeyStore(certChain.path, notAKey.path)
		}
	}
}
