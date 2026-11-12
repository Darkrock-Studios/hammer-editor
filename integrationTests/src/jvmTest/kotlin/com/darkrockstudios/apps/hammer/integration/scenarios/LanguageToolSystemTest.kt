package com.darkrockstudios.apps.hammer.integration.scenarios

import com.darkrockstudios.apps.hammer.admin.AdminServerConfig
import com.darkrockstudios.apps.hammer.common.dependencyinjection.APP_SCOPE
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.server.HammerServiceClient
import com.darkrockstudios.apps.hammer.common.server.ServiceReply
import com.darkrockstudios.apps.hammer.common.server.ServiceRequest
import com.darkrockstudios.apps.hammer.integration.RoundTripTestBase
import com.darkrockstudios.apps.hammer.operations.plugin.PluginRegistry
import com.darkrockstudios.apps.hammer.operations.plugin.TextDiagnosticsRequest
import com.darkrockstudios.apps.hammer.plugins.wasmhost.RuntimePlugins
import com.darkrockstudios.apps.hammer.services.languagetool.LanguageToolConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okio.Path.Companion.toPath
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.koin.core.context.loadKoinModules
import org.koin.core.context.unloadKoinModules
import org.koin.core.qualifier.named
import org.koin.test.get
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Grammar checking end to end: the real client stack and sync server, forwarding to a real LanguageTool.
 * Runs when LANGUAGETOOL_URL names one, such as `docker run -p 127.0.0.1:8010:8010 erikvl87/languagetool`;
 * the plugin test also needs HAMMER_PLUGINS, a hammer-plugins checkout with languagetool built.
 */
@EnabledIfEnvironmentVariable(named = "LANGUAGETOOL_URL", matches = ".+")
class LanguageToolSystemTest : RoundTripTestBase() {

	override val serverConfig = super.serverConfig.copy(languageToolEnabled = true)

	private val check = ServiceRequest(
		service = "languagetool",
		method = "POST",
		path = "/v2/check",
		headers = listOf("Content-Type" to "application/x-www-form-urlencoded"),
		body = "text=This+are+a+test.&language=en-US".encodeToByteArray(),
	)

	private fun pointAtLanguageTool() {
		database().serverDatabase.serverConfigQueries.upsertConfig(
			AdminServerConfig.LANGUAGETOOL_CONFIG.key,
			Json.encodeToString(LanguageToolConfig.serializer(), LanguageToolConfig(url = System.getenv("LANGUAGETOOL_URL"))),
		)
	}

	private suspend fun signIn(bearerToken: String = authToken.auth) {
		get<GlobalSettingsStore>().updateServerSettings(makeServerSettings().copy(bearerToken = bearerToken))
	}

	private fun ruleIds(body: ByteArray): List<String> =
		Json.parseToJsonElement(body.decodeToString()).jsonObject["matches"]!!.jsonArray
			.map { it.jsonObject["rule"]!!.jsonObject["id"]!!.jsonPrimitive.content }

	@Test
	@Timeout(60)
	fun `a check reaches LanguageTool through the sync server`() = runBlocking {
		pointAtLanguageTool()
		signIn()

		val reply = get<HammerServiceClient>().request(check)

		assertIs<ServiceReply.Response>(reply)
		assertEquals(200, reply.status)
		assertTrue("PLURAL_VERB_AFTER_THIS" in ruleIds(reply.body), reply.body.decodeToString())
	}

	@Test
	@Timeout(60)
	fun `an expired token is refreshed and the check still goes through`() = runBlocking {
		pointAtLanguageTool()
		signIn(bearerToken = "expired")

		val reply = get<HammerServiceClient>().request(check)

		assertIs<ServiceReply.Response>(reply)
		assertEquals(200, reply.status)
		assertTrue(get<GlobalSettingsStore>().serverSettings!!.bearerToken != "expired")
	}

	@Test
	@Timeout(60)
	@EnabledIfEnvironmentVariable(named = "HAMMER_PLUGINS", matches = ".+")
	fun `the LanguageTool plugin underlines through the sync server`() = runBlocking {
		pointAtLanguageTool()
		signIn()
		val built = File(System.getenv("HAMMER_PLUGINS"), "languagetool/build/languagetool.hammerplugin")
		check(built.exists()) { "Run ./build.sh languagetool in hammer-plugins first" }
		val download = "/downloads/languagetool.hammerplugin".toPath()
		fileSystem.createDirectories(download.parent!!)
		fileSystem.write(download) { write(built.readBytes()) }
		val plugins = RuntimePlugins(fileSystem, "/client/plugins".toPath(), "/client/cache/plugins".toPath(), null)
		plugins.install(download)
		val registry = PluginRegistry().also(plugins::activate)
		// The app supplies its scope per platform, so the client's mainModule leaves it out.
		val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
		val registryModule = registry.koinModule().apply {
			single(named(APP_SCOPE)) { appScope }
		}
		loadKoinModules(registryModule)
		try {
			registry.settings("languagetool")!!.set("service", JsonPrimitive("hammer"))
			val paragraph = "This are a test."

			val found = registry.plugins.single().textDiagnostics().single()
				.diagnose(TextDiagnosticsRequest(listOf(paragraph), "en-US", "Project")).single()

			assertTrue(found.any { paragraph.substring(it.start, it.end) == "are" && it.fixes.any { fix -> fix.replacement == "is" } }, "$found")
		} finally {
			unloadKoinModules(registryModule)
			appScope.cancel()
		}
	}
}
