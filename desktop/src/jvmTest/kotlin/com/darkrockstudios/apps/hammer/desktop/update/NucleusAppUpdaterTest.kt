package com.darkrockstudios.apps.hammer.desktop.update

import com.darkrockstudios.apps.hammer.common.data.appupdate.AppUpdateState
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import dev.nucleusframework.updater.NucleusUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.net.InetSocketAddress
import java.nio.file.Files
import java.security.MessageDigest
import java.util.Base64
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Drives the real Nucleus updater against a local HTTP server standing in for the GitHub
 * release, so the check, the download and the digest check are the production code paths.
 * Only the final hand-over to the installer is replaced, since the real one exits the process.
 */
class NucleusAppUpdaterTest {

	private val asset = ByteArray(60_000) { (it * 7 % 251).toByte() }
	private var servedVersion = "9.9.9"
	private var servedSha512 = sha512Base64(asset)
	private var metadataStatus = 200

	private lateinit var server: HttpServer
	private lateinit var scope: CoroutineScope
	private lateinit var cacheDir: File

	@BeforeEach
	fun start() {
		server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
		server.createContext("/") { exchange ->
			val path = exchange.requestURI.path
			when {
				path.endsWith(".yml") && metadataStatus != 200 -> respond(exchange, metadataStatus, ByteArray(0))
				path.endsWith(".yml") -> respond(exchange, 200, metadata().toByteArray())
				path.endsWith("/hammer.msi") -> respond(exchange, 200, asset)
				else -> respond(exchange, 404, ByteArray(0))
			}
		}
		server.start()
		scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
		cacheDir = Files.createTempDirectory("hammer-update-test").toFile()
	}

	@AfterEach
	fun stop() {
		scope.cancel()
		server.stop(0)
		cacheDir.deleteRecursively()
	}

	@Test
	fun `a newer release is reported as available`() = runBlocking {
		val updater = newUpdater()

		updater.checkNow()

		val available = updater.awaitState<AppUpdateState.Available>()
		assertEquals(AppUpdateState.Available("9.9.9", installable = true, dismissed = false), available)
	}

	@Test
	fun `the installed version is up to date`() = runBlocking {
		servedVersion = "1.0.0"
		val updater = newUpdater()

		updater.checkNow()

		assertEquals(AppUpdateState.UpToDate, updater.awaitState<AppUpdateState.UpToDate>())
	}

	@Test
	fun `a failed check is swallowed`() = runBlocking {
		metadataStatus = 503
		val updater = newUpdater()
		val seen = updater.recordStates()

		updater.checkNow()

		await { seen.contains(AppUpdateState.Checking) && seen.last() == AppUpdateState.Idle }
		assertFalse(seen.any { it is AppUpdateState.Failed })
	}

	@Test
	fun `a release this install cannot apply is announced but not installable`() = runBlocking {
		val updater = newUpdater(installable = false)

		updater.checkNow()
		val available = updater.awaitState<AppUpdateState.Available>()
		assertFalse(available.installable)

		updater.update()
		delay(200.milliseconds)
		assertEquals(available, updater.state.value)
	}

	@Test
	fun `a version the user dismissed stays dismissed`() = runBlocking {
		val updater = newUpdater(preferences = FakeUpdatePreferences(dismissedVersion = "9.9.9"))

		updater.checkNow()

		assertTrue(updater.awaitState<AppUpdateState.Available>().dismissed)
	}

	@Test
	fun `dismiss hides the version and records it`() = runBlocking {
		val preferences = FakeUpdatePreferences()
		val updater = newUpdater(preferences = preferences)
		updater.checkNow()
		updater.awaitState<AppUpdateState.Available>()

		updater.dismiss()

		assertTrue((updater.state.value as AppUpdateState.Available).dismissed)
		await { preferences.dismissedVersion == "9.9.9" }
	}

	@Test
	fun `update downloads, verifies, and hands the installer over`() = runBlocking {
		var installed: File? = null
		var installedBytes: ByteArray? = null
		val updater = newUpdater(install = { file ->
			installedBytes = file.readBytes()
			installed = file
		})
		val seen = updater.recordStates()
		updater.checkNow()
		updater.awaitState<AppUpdateState.Available>()

		updater.update()

		updater.awaitState<AppUpdateState.Installing>()
		await { installed != null }
		assertTrue(asset.contentEquals(installedBytes))
		assertEquals("hammer.msi", installed?.name)
		assertTrue(seen.any { it is AppUpdateState.Downloading && it.fraction == 1f })
	}

	@Test
	fun `a corrupt download fails instead of installing`() = runBlocking {
		servedSha512 = sha512Base64(ByteArray(10))
		var installed = false
		val updater = newUpdater(install = { installed = true })
		updater.checkNow()
		updater.awaitState<AppUpdateState.Available>()

		updater.update()

		val failed = updater.awaitState<AppUpdateState.Failed>()
		assertEquals("9.9.9", failed.version)
		assertFalse(installed)
	}

	@Test
	fun `automatic checks obey the preference`() = runBlocking {
		val preferences = FakeUpdatePreferences(automaticChecks = false)
		val updater = newUpdater(
			preferences = preferences,
			initialDelay = Duration.ZERO,
			checkInterval = 50.milliseconds,
		)
		val seen = updater.recordStates()

		updater.start()
		delay(400.milliseconds)
		assertFalse(seen.contains(AppUpdateState.Checking))
		assertNull(updater.state.value as? AppUpdateState.Available)

		preferences.automaticChecks = true
		assertEquals("9.9.9", updater.awaitState<AppUpdateState.Available>().version)
	}

	private fun newUpdater(
		installable: Boolean = true,
		preferences: FakeUpdatePreferences = FakeUpdatePreferences(),
		initialDelay: Duration = 10.seconds,
		checkInterval: Duration = 6.hours,
		install: (File) -> Unit = {},
	): NucleusAppUpdater {
		val nucleus = NucleusUpdater {
			currentVersion = "1.0.0"
			provider = HammerReleaseProvider("http://127.0.0.1:${server.address.port}")
			executableType = "msi"
			differentialDownload = false
			cacheDir = this@NucleusAppUpdaterTest.cacheDir
		}
		return NucleusAppUpdater(
			updater = nucleus,
			installable = installable,
			preferences = preferences,
			scope = scope,
			ioDispatcher = Dispatchers.IO,
			initialDelay = initialDelay,
			checkInterval = checkInterval,
			install = install,
		)
	}

	private fun metadata(): String = listOf(
		"version: $servedVersion",
		"releaseDate: 2026-10-20T00:00:00Z",
		"files:",
		"  - url: hammer.msi",
		"    sha512: $servedSha512",
		"    size: ${asset.size}",
	).joinToString("\n")

	private fun NucleusAppUpdater.recordStates(): List<AppUpdateState> {
		val seen = java.util.concurrent.CopyOnWriteArrayList<AppUpdateState>()
		scope.launch { state.collect { seen += it } }
		return seen
	}

	private suspend inline fun <reified T : AppUpdateState> NucleusAppUpdater.awaitState(): T =
		withTimeout(TIMEOUT) { state.first { it is T } as T }

	private suspend fun await(condition: () -> Boolean) {
		withTimeout(TIMEOUT) {
			while (!condition()) delay(20.milliseconds)
		}
	}

	private fun respond(exchange: HttpExchange, status: Int, body: ByteArray) {
		exchange.sendResponseHeaders(status, if (body.isEmpty()) -1L else body.size.toLong())
		exchange.responseBody.use { it.write(body) }
	}

	private fun sha512Base64(bytes: ByteArray): String =
		Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-512").digest(bytes))

	private companion object {
		val TIMEOUT = 20.seconds
	}
}
