package dependencyinjection

import com.darkrockstudios.apps.hammer.base.http.createJsonSerializer
import com.darkrockstudios.apps.hammer.base.http.writeJson
import com.darkrockstudios.apps.hammer.base.http.writeToml
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettings
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.globalsettings.ServerSettings
import com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource.AuthTokenStore
import com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource.AuthTokens
import com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource.FileAuthTokenStore
import com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource.GlobalSettingsDatasource
import com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource.GlobalSettingsFilesystemDatasource
import com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource.ServerSettingsDatasource
import com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource.ServerSettingsFilesystemDatasource
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.common.dependencyinjection.managedStorageRoots
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.ContainedFileSystem
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.spellcheck.LanguageUtil
import com.darkrockstudios.libs.platformspellchecker.PlatformSpellCheckerFactory
import io.mockk.mockk
import net.peanuuutz.tomlkt.Toml
import okio.FileSystem
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.Koin
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlinx.serialization.json.Json
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Reproduces the v3.5.0 prod hang: when a legacy `server.json` carries inline auth
 * tokens, [GlobalSettingsStore]'s construction performs a guarded write during its
 * own init, whose containment check resolves the projects root by re-entering the
 * store — an infinite construction loop that ANRs on the main thread.
 */
class GlobalSettingsBootstrapCycleTest {

	private lateinit var rawFs: FakeFileSystem
	private lateinit var json: Json
	private lateinit var toml: Toml
	private lateinit var languageUtil: LanguageUtil
	private lateinit var koin: Koin

	@BeforeEach
	fun setup() {
		rawFs = FakeFileSystem()
		json = createJsonSerializer()
		toml = createTomlSerializer()
		languageUtil = mockk(relaxed = true)

		koin = startKoin {
			modules(
				module {
					single { json }
					single { toml }
					single { languageUtil }
					single { PlatformSpellCheckerFactory() }
					single<FileSystem> { ContainedFileSystem(rawFs) { managedStorageRoots(getKoin()) } }
					single<AuthTokenStore> { FileAuthTokenStore(get(), get()) }
					single { GlobalSettingsFilesystemDatasource(get(), get(), get(), get()) }
					single<GlobalSettingsDatasource> { get<GlobalSettingsFilesystemDatasource>() }
					single<ServerSettingsDatasource> {
						CountingServerSettingsDatasource(
							ServerSettingsFilesystemDatasource(get(), get(), get())
						)
					}
					single { GlobalSettingsStore(get(), get()) }
				}
			)
		}.koin
	}

	@AfterEach
	fun tearDown() {
		stopKoin()
	}

	@Test
	fun `Construction is a pure read and the migration relocates tokens without looping`() {
		val projectsDir = GlobalSettingsStore.defaultProjectDir()
		// A real legacy device has both files; seeding only server.json would instead trip
		// the unrelated fresh-install config-write path.
		rawFs.createDirectories(GlobalSettingsFilesystemDatasource.CONFIG_PATH.parent!!)
		rawFs.writeToml(
			GlobalSettingsFilesystemDatasource.CONFIG_PATH,
			toml,
			GlobalSettings(projectsDirectory = projectsDir.toString()),
		)

		val serverJson = projectsDir / ServerSettingsFilesystemDatasource.SERVER_FILE_NAME
		rawFs.createDirectories(projectsDir)
		rawFs.writeJson(serverJson, json, legacyServerSettings())

		// Precondition: the seeded file really does carry inline tokens.
		assertTrue(rawFs.read(serverJson) { readUtf8() }.contains("zxc456"))

		// Constructing the store must not loop, and reads the inline tokens for the session.
		val store = koin.get<GlobalSettingsStore>()
		val counter = koin.get<ServerSettingsDatasource>() as CountingServerSettingsDatasource

		assertEquals(1, counter.loadCalls, "Store construction must not re-enter loadServerSettings")
		assertEquals("zxc456", store.serverSettings?.bearerToken)
		// Construction is pure: the file is untouched until the migration runs.
		assertTrue(rawFs.read(serverJson) { readUtf8() }.contains("zxc456"))

		// The migration writes through the guarded filesystem (where the cycle lived) after
		// the store is built — it must relocate the tokens without re-entering the store.
		koin.get<ServerSettingsDatasource>().migrateInlineTokens(projectsDir.toHPath())

		val rewritten = rawFs.read(serverJson) { readUtf8() }
		assertFalse(rewritten.contains("zxc456"))
		assertFalse(rewritten.contains("bnm789"))
		assertEquals(AuthTokens("zxc456", "bnm789"), koin.get<AuthTokenStore>().get("hammer.ink", 1L))
		assertEquals(1, counter.loadCalls, "Migration must not trigger more settings loads")
	}

	@Test
	fun `Fresh install with no config constructs the store without looping`() {
		// The original crash: a fresh install (no config file) had the datasource write
		// defaults from its init block, which re-entered the still-constructing store
		// through the guarded filesystem and recursed until the stack overflowed. Seed
		// nothing and assert construction is a pure read that completes.
		assertFalse(rawFs.exists(GlobalSettingsFilesystemDatasource.CONFIG_PATH))

		val store = koin.get<GlobalSettingsStore>()
		val counter = koin.get<ServerSettingsDatasource>() as CountingServerSettingsDatasource

		// Construction wrote nothing — defaults live in memory until a real store happens.
		assertFalse(
			rawFs.exists(GlobalSettingsFilesystemDatasource.CONFIG_PATH),
			"Store construction must not write the config on a fresh install",
		)
		assertEquals(1, counter.loadCalls, "Store construction must not re-enter loadServerSettings")
		assertEquals(
			GlobalSettingsStore.defaultProjectDir().toString(),
			store.globalSettings.projectsDirectory,
		)
	}

	private fun legacyServerSettings() = ServerSettings(
		ssl = true,
		url = "hammer.ink",
		email = "test@example.com",
		userId = 1,
		bearerToken = "zxc456",
		refreshToken = "bnm789",
	)

	/** Counts load calls and hard-caps recursion so a regression fails fast instead of hanging. */
	private class CountingServerSettingsDatasource(
		private val delegate: ServerSettingsDatasource,
	) : ServerSettingsDatasource {
		var loadCalls = 0
			private set

		override fun serverIsSetup(projectsDir: HPath) = delegate.serverIsSetup(projectsDir)

		override fun loadServerSettings(projectsDir: HPath): ServerSettings? {
			loadCalls++
			check(loadCalls <= MAX_CALLS) { "loadServerSettings re-entered $loadCalls times — bootstrap cycle regression" }
			return delegate.loadServerSettings(projectsDir)
		}

		override fun storeServerSettings(settings: ServerSettings, projectsDir: HPath) =
			delegate.storeServerSettings(settings, projectsDir)

		override fun removeServerSettings(projectsDir: HPath) = delegate.removeServerSettings(projectsDir)

		override fun migrateInlineTokens(projectsDir: HPath) = delegate.migrateInlineTokens(projectsDir)

		companion object {
			private const val MAX_CALLS = 25
		}
	}
}
