package plugin

import com.darkrockstudios.apps.hammer.common.data.secrets.SecretStore
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.operations.plugin.PluginSettingsDatasource
import com.darkrockstudios.apps.hammer.operations.plugin.SettingDeclaration
import com.darkrockstudios.apps.hammer.operations.plugin.parseSettingDeclarations
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull

class SecretSettingsTest {

	private class MemorySecrets : SecretStore {
		val values = mutableMapOf<String, String>()
		override fun get(key: String) = values[key]
		override fun put(key: String, value: String) {
			if (value.isEmpty()) values.remove(key) else values[key] = value
		}
		override fun removeAll(prefix: String) {
			values.keys.removeAll { it.startsWith(prefix) }
		}
	}

	private val fileSystem = FakeFileSystem()
	private val pluginsDir = "/config/plugins".toPath()
	private val secrets = MemorySecrets()
	private val datasource = PluginSettingsDatasource(fileSystem, createTomlSerializer(), pluginsDir, secrets)
	private val declarations = listOf(
		SettingDeclaration.Text("user", "User"),
		SettingDeclaration.Secret("apiKey", "API key"),
	)

	private fun settingsFile() = fileSystem.read(pluginsDir / "lt.toml") { readUtf8() }

	@Test
	fun `a secret is parsed, and may not have a default`() {
		val parsed = parseSettingDeclarations("[[setting]]\nkey = \"apiKey\"\ntype = \"secret\"\nlabel = \"API key\"\n")
		assertIs<SettingDeclaration.Secret>(parsed.single())
		assertFailsWith<IllegalArgumentException> {
			parseSettingDeclarations("[[setting]]\nkey = \"apiKey\"\ntype = \"secret\"\nlabel = \"API key\"\ndefault = \"x\"\n")
		}
	}

	@Test
	fun `a secret goes to the secret store, never the settings file`() {
		datasource.storeDeclared("lt", declarations, JsonObject(mapOf("user" to JsonPrimitive("me"), "apiKey" to JsonPrimitive("k3y"))))

		assertEquals("k3y", secrets.values["plugin/lt/apiKey"])
		assertFalse("k3y" in settingsFile())
		assertEquals(JsonPrimitive("k3y"), datasource.loadDeclared("lt", declarations)["apiKey"])
		assertEquals(JsonPrimitive("me"), datasource.loadDeclared("lt", declarations)["user"])
	}

	@Test
	fun `an unset secret is empty`() {
		assertEquals(JsonPrimitive(""), datasource.loadDeclared("lt", declarations)["apiKey"])
	}

	@Test
	fun `a secret saved as plain text before moves to the secret store`() {
		fileSystem.createDirectories(pluginsDir)
		fileSystem.write(pluginsDir / "lt.toml") { writeUtf8("user = \"me\"\napiKey = \"old\"\n") }

		assertEquals(JsonPrimitive("old"), datasource.loadDeclared("lt", declarations)["apiKey"])
		assertEquals("old", secrets.values["plugin/lt/apiKey"])
		assertFalse("old" in settingsFile())
	}

	@Test
	fun `removing a plugin's secrets leaves other plugins' alone`() {
		secrets.put("plugin/lt/apiKey", "a")
		secrets.put("plugin/lt2/apiKey", "b")

		datasource.removeSecrets("lt")

		assertNull(secrets.get("plugin/lt/apiKey"))
		assertEquals("b", secrets.get("plugin/lt2/apiKey"))
	}
}
