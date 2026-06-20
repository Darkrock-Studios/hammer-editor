package com.darkrockstudios.apps.hammer.utils

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.cert.X509Certificate
import java.util.Date

/** Helpers for generating self-signed certs/keys to exercise the SSL loading paths. */
internal object TestCerts {

	fun selfSigned(cn: String = "localhost"): Pair<KeyPair, X509Certificate> {
		val keyPair =
			KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

		val now = System.currentTimeMillis()
		val name = X500Name("CN=$cn")
		val builder = JcaX509v3CertificateBuilder(
			name,
			BigInteger.valueOf(now),
			Date(now - 60_000L),
			Date(now + 86_400_000L),
			name,
			keyPair.public,
		)
		val signer = JcaContentSignerBuilder("SHA256withRSA").build(keyPair.private)
		val cert = JcaX509CertificateConverter().getCertificate(builder.build(signer))
		return keyPair to cert
	}

	/** Writes a PEM-encoded object (certificate, private key, etc.) to [file]. */
	fun writePem(file: File, obj: Any) {
		JcaPEMWriter(file.writer()).use { it.writeObject(obj) }
	}
}
