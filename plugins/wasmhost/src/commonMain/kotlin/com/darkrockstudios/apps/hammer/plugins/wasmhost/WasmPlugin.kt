package com.darkrockstudios.apps.hammer.plugins.wasmhost

import com.darkrockstudios.apps.hammer.common.data.export.ExportInput
import com.darkrockstudios.apps.hammer.common.data.export.StoryExporter
import com.darkrockstudios.apps.hammer.common.dependencyinjection.injectIoDispatcher
import com.darkrockstudios.apps.hammer.operations.OperationException
import com.darkrockstudios.apps.hammer.operations.OperationJson
import com.darkrockstudios.apps.hammer.operations.OperationRegistry
import com.darkrockstudios.apps.hammer.operations.plugin.ClientPlugin
import com.darkrockstudios.apps.hammer.operations.plugin.PluginRegistry
import com.darkrockstudios.apps.hammer.operations.plugin.SettingDeclaration
import io.github.aakira.napier.Napier
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okio.BufferedSink
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

/**
 * A runtime plugin: a manifest plus an Extism-convention module, presented as a [ClientPlugin].
 * The module is instantiated on first use, and calls into it run one at a time. It may dispatch only
 * operations that its manifest requests and the user [granted].
 */
class WasmPlugin(
	val manifest: PluginManifest,
	private val wasm: ByteArray,
	private val declaredSettings: List<SettingDeclaration> = emptyList(),
	private val granted: Set<String> = manifest.permissions.operations.toSet(),
	private val fuelPerCall: Long = DEFAULT_FUEL_PER_CALL,
) : ClientPlugin, KoinComponent {

	override val id: String = manifest.id
	override val name: String = manifest.name

	private val lock = reentrantLock()
	private val module by lazy {
		ExtismPlugin(wasm, listOf(dispatchFunction()), log = ::log)
	}

	private val ioDispatcher by injectIoDispatcher()

	override fun exporters(): List<StoryExporter> = manifest.exporters.map(::Exporter)

	override fun settings(): List<SettingDeclaration> = declaredSettings

	/** Loads the module now rather than on first use, which surfaces any problem with it as a [PluginException]. */
	fun instantiate() {
		module
	}

	/** Runs the module's [function] on [input], off the caller's thread. */
	suspend fun call(function: String, input: ByteArray): ByteArray =
		withContext(ioDispatcher) { callBlocking(function, input) }

	/**
	 * Runs the module's [function] on [input], blocking the calling thread for the whole call, operations
	 * the module dispatches included. Never call it from the main thread.
	 */
	fun callBlocking(function: String, input: ByteArray): ByteArray = lock.withLock {
		module.call(function, input, fuelPerCall)
	}

	private fun dispatchFunction() = ExtismPlugin.UserFunction(DISPATCH, params = 1, returnsValue = true) { args ->
		val reply = try {
			dispatch(OperationJson.decodeFromString<DispatchRequest>(read(args[0]).decodeToString()))
		} catch (e: IllegalArgumentException) {
			DispatchReply.failed(OperationException.Kind.InvalidInput.name, "Malformed dispatch request: ${e.message}")
		}
		write(OperationJson.encodeToString(reply).encodeToByteArray())
	}

	// Called from inside the module, so it blocks this call's thread until the operation finishes.
	private fun dispatch(request: DispatchRequest): DispatchReply {
		if (request.operation !in manifest.permissions.operations || request.operation !in granted) {
			return DispatchReply.failed(PERMISSION_DENIED, "Plugin '$id' has no permission for ${request.operation}")
		}
		return try {
			DispatchReply(output = runBlocking { get<OperationRegistry>().dispatch(request.operation, request.input) })
		} catch (e: OperationException) {
			DispatchReply.failed(e.kind.name, e.message.orEmpty())
		}
	}

	private fun log(level: ExtismPlugin.LogLevel, message: String) {
		val line = "Plugin '$id': $message"
		when (level) {
			ExtismPlugin.LogLevel.Error -> Napier.e(line)
			ExtismPlugin.LogLevel.Warn -> Napier.w(line)
			ExtismPlugin.LogLevel.Info -> Napier.i(line)
			else -> Napier.d(line)
		}
	}

	private inner class Exporter(private val format: PluginManifest.Exporter) : StoryExporter {
		override val formatId = format.format
		override val fileExtension = format.extension
		override val mimeType = format.mime
		override val label = format.label
		override val needsProjectData = false

		override fun render(sink: BufferedSink, input: ExportInput) {
			val request = ExportRequest(
				format = formatId,
				projectName = input.projectName,
				language = input.language,
				chapters = input.bookChapters().map { ExportRequest.Chapter(it.name, it.scenes) },
				settings = get<PluginRegistry>().settings(id)?.values?.value ?: JsonObject(emptyMap()),
			)
			// Export renders on a background dispatcher already.
			sink.write(callBlocking(EXPORT, OperationJson.encodeToString(request).encodeToByteArray()))
		}
	}

	@Serializable
	private class DispatchRequest(val operation: String, val input: JsonElement = JsonObject(emptyMap()))

	@Serializable
	private class DispatchReply(val output: JsonElement? = null, val error: Error? = null) {
		@Serializable
		class Error(val kind: String, val message: String)

		companion object {
			fun failed(kind: String, message: String) = DispatchReply(error = Error(kind, message))
		}
	}

	@Serializable
	private class ExportRequest(
		val format: String,
		val projectName: String,
		val language: String,
		val chapters: List<Chapter>,
		/** The plugin's declared settings, every key present. */
		val settings: JsonObject,
	) {
		@Serializable
		class Chapter(val name: String, val scenes: List<String>)
	}

	companion object {
		/** The import a module calls operations through, in `extism:host/user`. */
		const val DISPATCH = "hammer_dispatch"

		/** The export that renders every export format the manifest declares. */
		const val EXPORT = "export"

		const val PERMISSION_DENIED = "PermissionDenied"

		/** Function calls plus loop iterations allowed per call before the module is stopped. */
		const val DEFAULT_FUEL_PER_CALL = 2_000_000_000L
	}
}
