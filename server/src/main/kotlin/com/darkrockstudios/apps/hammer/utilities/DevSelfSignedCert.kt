package com.darkrockstudios.apps.hammer.utilities

import io.ktor.network.tls.certificates.buildKeyStore
import io.ktor.network.tls.certificates.saveToFile
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import java.io.File
import java.security.KeyStore

/**
 * A self-signed keystore used only when the server runs in `--dev` mode with no `sslCert`
 * configured. It lets the direct-connection dev loop (desktop client → local server, no
 * reverse proxy) speak HTTPS without provisioning a real certificate. The desktop `--dev`
 * client trusts it automatically; it is never used by a production build.
 */
object DevSelfSignedCert {
	private const val ALIAS = "server"
	private const val PASSWORD = "hammer-dev"
	private const val FILE_NAME = "dev-selfsigned.jks"

	/**
	 * Loads the persisted dev keystore, generating and saving one on first run. Persisting it
	 * keeps the certificate (and its fingerprint) stable across restarts instead of minting a
	 * fresh one every boot.
	 */
	fun getOrCreate(fileSystem: FileSystem = FileSystem.SYSTEM): DevKeyStore {
		val path: Path = getRootDataDirectory(fileSystem) / FILE_NAME
		return getOrCreate(File(path.toString()))
	}

	/** Testable core: reads or writes the keystore at an explicit [keyStoreFile]. */
	internal fun getOrCreate(keyStoreFile: File): DevKeyStore {
		val keyStore = if (keyStoreFile.exists()) {
			KeyStore.getInstance(keyStoreFile, PASSWORD.toCharArray())
		} else {
			val generated = buildKeyStore {
				certificate(ALIAS) {
					password = PASSWORD
					domains = listOf("localhost", "127.0.0.1", "0.0.0.0")
					daysValid = 3650
				}
			}
			keyStoreFile.parentFile?.mkdirs()
			generated.saveToFile(keyStoreFile, PASSWORD)
			generated
		}

		return DevKeyStore(
			keyStore = keyStore,
			alias = ALIAS,
			password = PASSWORD,
			path = keyStoreFile.path.toPath(),
		)
	}
}

data class DevKeyStore(
	val keyStore: KeyStore,
	val alias: String,
	val password: String,
	val path: Path,
)
