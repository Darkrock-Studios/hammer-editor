package com.darkrockstudios.apps.hammer

import com.darkrockstudios.apps.hammer.utils.TestCerts
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.security.KeyStore
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SslCertTest {

	@TempDir
	lateinit var tempDir: File

	private fun writePemFiles(): SslCertConfig {
		val (keyPair, cert) = TestCerts.selfSigned()
		val certChain = File(tempDir, "fullchain.pem").also { TestCerts.writePem(it, cert) }
		val privateKey =
			File(tempDir, "privkey.pem").also { TestCerts.writePem(it, keyPair.private) }
		return SslCertConfig(certChainPath = certChain.path, privateKeyPath = privateKey.path)
	}

	private fun writeKeystore(alias: String, password: String): File {
		val (keyPair, cert) = TestCerts.selfSigned()
		val keyStore = KeyStore.getInstance("PKCS12")
		keyStore.load(null, null)
		keyStore.setKeyEntry(alias, keyPair.private, password.toCharArray(), arrayOf(cert))
		return File(tempDir, "cert.p12").also { file ->
			file.outputStream().use { keyStore.store(it, password.toCharArray()) }
		}
	}

	// --- validate() / usePem() ---

	@Test
	fun `validate accepts a keystore config`() {
		assertTrue(SslCertConfig(path = "/cert.p12", storePassword = "pw").validate())
	}

	@Test
	fun `validate accepts a PEM config`() {
		assertTrue(
			SslCertConfig(
				certChainPath = "/fullchain.pem",
				privateKeyPath = "/privkey.pem"
			).validate()
		)
	}

	@Test
	fun `validate rejects an empty config`() {
		assertFalse(SslCertConfig().validate())
	}

	@Test
	fun `validate rejects a keystore path with no password`() {
		assertFalse(SslCertConfig(path = "/cert.p12").validate())
	}

	@Test
	fun `usePem is true only when both PEM paths are set`() {
		assertTrue(SslCertConfig(certChainPath = "/a", privateKeyPath = "/b").usePem())
		assertFalse(SslCertConfig(certChainPath = "/a").usePem())
		assertFalse(SslCertConfig(path = "/cert.p12", storePassword = "pw").usePem())
	}

	// --- getKeyStore() ---

	@Test
	fun `getKeyStore loads a keystore file from disk`() {
		val keystoreFile = writeKeystore(alias = "certificate", password = "secret")
		val config = SslCertConfig(
			path = keystoreFile.path,
			storePassword = "secret",
			keyAlias = "certificate",
			keyPassword = "secret",
		)

		val keyStore = getKeyStore(config)

		assertTrue(keyStore.containsAlias("certificate"))
	}

	@Test
	fun `getKeyStore routes a PEM config to the PEM loader`() {
		val keyStore = getKeyStore(writePemFiles())

		assertTrue(keyStore.containsAlias("server"))
	}

	@Test
	fun `getKeyStore throws when the keystore file is missing`() {
		val config =
			SslCertConfig(path = File(tempDir, "absent.p12").path, storePassword = "secret")

		assertFailsWith<IllegalArgumentException> { getKeyStore(config) }
	}

	@Test
	fun `getKeyStore throws when the keystore password is missing`() {
		val keystoreFile = writeKeystore(alias = "certificate", password = "secret")
		val config = SslCertConfig(path = keystoreFile.path)

		assertFailsWith<IllegalStateException> { getKeyStore(config) }
	}
}
