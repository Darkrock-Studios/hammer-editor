package com.darkrockstudios.apps.hammer.patreon

import com.darkrockstudios.apps.hammer.utils.BaseTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PatreonApiClientTest : BaseTest() {

	private lateinit var client: PatreonApiClient
	private val logger = LoggerFactory.getLogger(PatreonApiClientTest::class.java)

	@BeforeEach
	override fun setup() {
		super.setup()
		client = PatreonApiClient(logger)
	}

	@Test
	fun `verify valid webhook signature`() {
		val payload = """{"data":{"id":"123"}}"""
		val secret = "test-webhook-secret"

		// Compute expected signature using HmacMD5
		val mac = javax.crypto.Mac.getInstance("HmacMD5")
		val secretKey = javax.crypto.spec.SecretKeySpec(secret.toByteArray(), "HmacMD5")
		mac.init(secretKey)
		val hash = mac.doFinal(payload.toByteArray())
		val expectedSignature = hash.joinToString("") { "%02x".format(it) }

		val result = client.verifyWebhookSignature(payload, expectedSignature, secret)

		assertTrue(result)
	}

	@Test
	fun `reject invalid webhook signature`() {
		val payload = """{"data":{"id":"123"}}"""
		val secret = "test-webhook-secret"
		val invalidSignature = "invalid-signature-12345"

		val result = client.verifyWebhookSignature(payload, invalidSignature, secret)

		assertFalse(result)
	}

	@Test
	fun `reject tampered payload`() {
		val originalPayload = """{"data":{"id":"123"}}"""
		val tamperedPayload = """{"data":{"id":"456"}}"""
		val secret = "test-webhook-secret"

		// Compute signature for original payload
		val mac = javax.crypto.Mac.getInstance("HmacMD5")
		val secretKey = javax.crypto.spec.SecretKeySpec(secret.toByteArray(), "HmacMD5")
		mac.init(secretKey)
		val hash = mac.doFinal(originalPayload.toByteArray())
		val originalSignature = hash.joinToString("") { "%02x".format(it) }

		// Verify with tampered payload should fail
		val result = client.verifyWebhookSignature(tamperedPayload, originalSignature, secret)

		assertFalse(result)
	}

	@Test
	fun `reject wrong secret`() {
		val payload = """{"data":{"id":"123"}}"""
		val correctSecret = "correct-secret"
		val wrongSecret = "wrong-secret"

		// Compute signature with correct secret
		val mac = javax.crypto.Mac.getInstance("HmacMD5")
		val secretKey = javax.crypto.spec.SecretKeySpec(correctSecret.toByteArray(), "HmacMD5")
		mac.init(secretKey)
		val hash = mac.doFinal(payload.toByteArray())
		val signature = hash.joinToString("") { "%02x".format(it) }

		// Verify with wrong secret should fail
		val result = client.verifyWebhookSignature(payload, signature, wrongSecret)

		assertFalse(result)
	}

	@Test
	fun `signature verification is case insensitive`() {
		val payload = """{"data":{"id":"123"}}"""
		val secret = "test-webhook-secret"

		// Compute expected signature
		val mac = javax.crypto.Mac.getInstance("HmacMD5")
		val secretKey = javax.crypto.spec.SecretKeySpec(secret.toByteArray(), "HmacMD5")
		mac.init(secretKey)
		val hash = mac.doFinal(payload.toByteArray())
		val lowerCaseSignature = hash.joinToString("") { "%02x".format(it) }
		val upperCaseSignature = lowerCaseSignature.uppercase()

		// Both should verify successfully
		assertTrue(client.verifyWebhookSignature(payload, lowerCaseSignature, secret))
		assertTrue(client.verifyWebhookSignature(payload, upperCaseSignature, secret))
	}

	@Test
	fun `empty payload signature verification`() {
		val payload = ""
		val secret = "test-webhook-secret"

		// Compute expected signature for empty payload
		val mac = javax.crypto.Mac.getInstance("HmacMD5")
		val secretKey = javax.crypto.spec.SecretKeySpec(secret.toByteArray(), "HmacMD5")
		mac.init(secretKey)
		val hash = mac.doFinal(payload.toByteArray())
		val expectedSignature = hash.joinToString("") { "%02x".format(it) }

		val result = client.verifyWebhookSignature(payload, expectedSignature, secret)

		assertTrue(result)
	}

	@Test
	fun `empty signature returns false`() {
		val payload = """{"data":{"id":"123"}}"""
		val secret = "test-webhook-secret"

		val result = client.verifyWebhookSignature(payload, "", secret)

		assertFalse(result)
	}
}
