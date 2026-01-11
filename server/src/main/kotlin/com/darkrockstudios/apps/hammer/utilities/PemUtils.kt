package com.darkrockstudios.apps.hammer.utilities

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.openssl.PEMKeyPair
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import java.io.File
import java.io.FileReader
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate

/**
 * Loads PEM certificate chain and private key files (e.g., from Let's Encrypt)
 * and creates an in-memory KeyStore that can be used with Ktor's SSL configuration.
 *
 * @param certChainPath Path to the certificate chain file (e.g., fullchain.pem)
 * @param privateKeyPath Path to the private key file (e.g., privkey.pem)
 * @param keyAlias Alias to use for the key entry in the keystore (default: "server")
 * @param keyPassword Password to protect the private key (default: empty)
 * @return A KeyStore containing the certificate chain and private key
 */
fun loadPemAsKeyStore(
	certChainPath: String,
	privateKeyPath: String,
	keyAlias: String = "server",
	keyPassword: String = ""
): KeyStore {
	val certChainFile = File(certChainPath)
	val privateKeyFile = File(privateKeyPath)

	require(certChainFile.exists()) { "Certificate chain file not found: $certChainPath" }
	require(privateKeyFile.exists()) { "Private key file not found: $privateKeyPath" }

	val certificates = loadCertificateChain(certChainFile)
	require(certificates.isNotEmpty()) { "No certificates found in $certChainPath" }

	val privateKey = loadPrivateKey(privateKeyFile)

	// Create in-memory PKCS12 keystore
	val keyStore = KeyStore.getInstance("PKCS12")
	keyStore.load(null, null)

	// Add the private key with certificate chain
	keyStore.setKeyEntry(
		keyAlias,
		privateKey,
		keyPassword.toCharArray(),
		certificates.toTypedArray()
	)

	return keyStore
}

private fun loadCertificateChain(certFile: File): List<X509Certificate> {
	val certificates = mutableListOf<X509Certificate>()
	val converter = JcaX509CertificateConverter()

	PEMParser(FileReader(certFile)).use { parser ->
		var obj = parser.readObject()
		while (obj != null) {
			if (obj is X509CertificateHolder) {
				certificates.add(converter.getCertificate(obj))
			}
			obj = parser.readObject()
		}
	}

	return certificates
}

private fun loadPrivateKey(keyFile: File): PrivateKey {
	val converter = JcaPEMKeyConverter()

	PEMParser(FileReader(keyFile)).use { parser ->
		var obj = parser.readObject()
		while (obj != null) {
			when (obj) {
				is PEMKeyPair -> return converter.getKeyPair(obj).private
				is PrivateKeyInfo -> return converter.getPrivateKey(obj)
			}
			obj = parser.readObject()
		}
	}

	throw IllegalArgumentException("No private key found in ${keyFile.path}")
}
