package com.darkrockstudios.apps.hammer.plugins.wasmhost

import com.darkrockstudios.apps.hammer.common.data.export.ExportInput
import com.darkrockstudios.apps.hammer.common.data.export.StoryExporter
import com.darkrockstudios.apps.hammer.common.dependencyinjection.injectIoDispatcher
import com.darkrockstudios.apps.hammer.operations.OperationException
import com.darkrockstudios.apps.hammer.operations.OperationJson
import com.darkrockstudios.apps.hammer.operations.OperationRegistry
import com.darkrockstudios.apps.hammer.operations.cli.CliCommand
import com.darkrockstudios.apps.hammer.operations.cli.CliIo
import com.darkrockstudios.apps.hammer.operations.cli.Dispatcher
import com.darkrockstudios.apps.hammer.operations.core.OPS_LIST
import com.darkrockstudios.apps.hammer.operations.core.OperationDescriptor
import com.darkrockstudios.apps.hammer.operations.core.OperationList
import com.darkrockstudios.apps.hammer.operations.core.descriptor
import com.darkrockstudios.apps.hammer.operations.plugin.ClientPlugin
import com.darkrockstudios.apps.hammer.operations.plugin.PluginRegistry
import com.darkrockstudios.apps.hammer.operations.plugin.ProjectAction
import com.darkrockstudios.apps.hammer.operations.plugin.SettingDeclaration
import io.github.aakira.napier.Napier
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okio.BufferedSink
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

/**
 * A runtime plugin: a manifest plus an Extism-convention module, presented as a [ClientPlugin].
 * The module is instantiated on first use, and calls into it run one at a time. It may dispatch only
 * operations covered by an [OperationGrant] that its manifest requests and the user [granted], and
 * `ops.list` shows it only those.
 */
class WasmPlugin(
	val manifest: PluginManifest,
	/**
	 * Called when the module is first needed, so the bytes are not held until then, and again after a call
	 * that left it holding more than [releaseGuestHeapAbove].
	 */
	private val loadModule: () -> ByteArray,
	private val declaredSettings: List<SettingDeclaration> = emptyList(),
	granted: Set<String> = manifest.permissions.operations.toSet(),
	/** The plugin's declared settings as saved, for commands, which run without the rest of Hammer. */
	private val savedSettings: () -> JsonObject = { JsonObject(emptyMap()) },
	/** Where [CACHE_GET] and [CACHE_SET] keep values; without one, every get misses. */
	private val cache: PluginCache? = null,
	private val fuelPerCall: Long = DEFAULT_FUEL_PER_CALL,
	private val releaseGuestHeapAbove: Long = DEFAULT_RELEASE_GUEST_HEAP_ABOVE,
) : ClientPlugin, KoinComponent {

	override val id: String = manifest.id
	override val name: String = manifest.name

	private val grants: List<OperationGrant> =
		manifest.permissions.operations.filter { it in granted }.mapNotNull(OperationGrant::parse)

	private val lock = reentrantLock()

	// Only touched while holding the lock.
	private var loaded: ExtismPlugin? = null
	private val module: ExtismPlugin
		get() = loaded ?: ExtismPlugin(loadModule(), listOf(dispatchFunction()) + cacheFunctions(), log = ::log)
			.also { loaded = it }

	private val appRoute by lazy { AppRoute(get()) }

	// The route of the call running now; only touched while holding the lock.
	private var activeRoute: Route? = null

	private val ioDispatcher by injectIoDispatcher()

	override fun exporters(): List<StoryExporter> = manifest.exporters.map(::Exporter)

	override fun settings(): List<SettingDeclaration> = declaredSettings

	override fun cliCommands(): List<CliCommand> = manifest.commands.map(::Command)

	override fun projectActions(): List<ProjectAction> = manifest.actions.map { action ->
		ProjectAction(action.label, document = action.output == PluginManifest.OUTPUT_DOCUMENT) { project ->
			val request = ActionRequest(action.name, project, settingsValues())
			call(ACTION, OperationJson.encodeToString(request).encodeToByteArray()).decodeToString().ifBlank { null }
		}
	}

	/** Loads the module now rather than on first use, which surfaces any problem with it as a [PluginException]. */
	fun instantiate() {
		lock.withLock { module }
	}

	/** Runs the module's [function] on [input], off the caller's thread. */
	suspend fun call(function: String, input: ByteArray): ByteArray =
		withContext(ioDispatcher) { callBlocking(function, input) }

	/**
	 * Runs the module's [function] on [input], blocking the calling thread for the whole call, operations
	 * the module dispatches included. Never call it from the main thread. The module's operations go
	 * through [route], by default the app's own registry.
	 */
	fun callBlocking(function: String, input: ByteArray, route: Route? = null): ByteArray = lock.withLock {
		val outer = activeRoute
		activeRoute = route ?: appRoute
		try {
			module.call(function, input, fuelPerCall)
		} finally {
			activeRoute = outer
			// chasm never gives back the guest heap a call grew, so a module that needed a lot is dropped and
			// loaded afresh when next used.
			if (outer == null && (loaded?.guestHeapBytes ?: 0) > releaseGuestHeapAbove) loaded = null
		}
	}

	/**
	 * Where a module's operations run. Asks [dispatcher] for the operations once, when first needed;
	 * the host answers `ops.list` from those itself, so listing needs no call into Hammer.
	 */
	open class Route(private val dispatcher: Dispatcher) {
		private var operations: Map<String, OperationDescriptor>? = null

		internal suspend fun operations(): Collection<OperationDescriptor> =
			(operations ?: dispatcher.operations().associateBy { it.name }.also { operations = it }).values

		internal open suspend fun find(name: String): OperationDescriptor? = operations().firstOrNull { it.name == name }

		internal suspend fun dispatch(name: String, input: JsonElement): JsonElement = dispatcher.dispatch(name, input)
	}

	// Looks up one operation in the registry, rather than describing them all, to check a dispatch.
	private class AppRoute(private val registry: OperationRegistry) : Route(RegistryDispatcher(registry)) {
		override suspend fun find(name: String) = registry.find(name)?.descriptor()
	}

	private class RegistryDispatcher(private val registry: OperationRegistry) : Dispatcher {
		override suspend fun dispatch(operation: String, input: JsonElement) = registry.dispatch(operation, input)
		override suspend fun operations() = registry.operations.map { it.descriptor(it.inputSchema()) }
	}

	private fun dispatchFunction() = ExtismPlugin.UserFunction(DISPATCH, params = 1, returnsValue = true) { args ->
		val reply = try {
			dispatch(OperationJson.decodeFromString<DispatchRequest>(read(args[0]).decodeToString()))
		} catch (e: IllegalArgumentException) {
			DispatchReply.failed(OperationException.Kind.InvalidInput.name, "Malformed dispatch request: ${e.message}")
		}
		write(OperationJson.encodeToString(reply).encodeToByteArray())
	}

	private fun cacheFunctions() = listOf(
		ExtismPlugin.UserFunction(CACHE_GET, params = 1, returnsValue = true) { args ->
			cache?.get(read(args[0]))?.let(::write) ?: 0
		},
		ExtismPlugin.UserFunction(CACHE_SET, params = 2, returnsValue = false) { args ->
			cache?.set(read(args[0]), if (args[1] == 0L) null else read(args[1]))
			0
		},
	)

	// Called from inside the module, so it blocks this call's thread until the operation finishes.
	private fun dispatch(request: DispatchRequest): DispatchReply = runBlocking {
		val route = checkNotNull(activeRoute) { "Dispatch outside a call" }
		try {
			val op = route.find(request.operation)
				?: return@runBlocking DispatchReply.failed(OperationException.Kind.NotFound.name, "No operation named '${request.operation}'")
			if (grants.none { it.covers(op) }) {
				return@runBlocking DispatchReply.failed(PERMISSION_DENIED, "Plugin '$id' has no permission for ${op.name}")
			}
			val output = if (op.name == OPS_LIST) {
				val permitted = route.operations().filter { listed -> grants.any { it.covers(listed) } }
				OperationJson.encodeToJsonElement(OperationList.serializer(), OperationList(permitted))
			} else {
				route.dispatch(op.name, request.input)
			}
			DispatchReply(output = output)
		} catch (e: OperationException) {
			DispatchReply.failed(e.kind.name, e.message.orEmpty())
		} catch (e: CancellationException) {
			throw e
		} catch (e: PluginException) {
			throw e
		} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
			// Such as a CLI command finding Hammer busy: the module can say so, where a trap would end it.
			Napier.e(e) { "Plugin '$id' dispatch of ${request.operation} failed" }
			DispatchReply.failed(FAILED, e.message ?: "Failed")
		}
	}

	private inner class Command(private val command: PluginManifest.Command) : CliCommand {
		override val name = command.name
		override val help = command.help

		override suspend fun run(args: List<String>, io: CliIo, dispatcher: Dispatcher): Int {
			try {
				instantiate()
			} catch (e: PluginException) {
				io.stderr.writeUtf8("Plugin '$id' could not start: ${e.message}\n")
				return COMMAND_FAILED
			}
			val route = Route(dispatcher)
			while (true) {
				val line = io.stdin.readUtf8Line() ?: return 0
				// Settings are read again for each line, so a change in Hammer applies at once.
				val request = CommandRequest(name, args, savedSettings(), line)
				val reply = try {
					callBlocking(COMMAND, OperationJson.encodeToString(request).encodeToByteArray(), route)
				} catch (e: PluginException) {
					io.stderr.writeUtf8("Plugin '$id' failed: ${e.message}\n")
					io.stderr.flush()
					continue
				}
				if (reply.isNotEmpty()) {
					io.stdout.write(reply)
					if (reply.last() != '\n'.code.toByte()) io.stdout.writeUtf8("\n")
					io.stdout.flush()
				}
			}
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

	// An export can start just as this plugin is removed or replaced, when the registry's store is not its own.
	private fun settingsValues(): JsonObject {
		val registry = get<PluginRegistry>()
		if (registry.plugins.none { it === this }) return savedSettings()
		return registry.settings(id)?.values?.value ?: JsonObject(emptyMap())
	}

	private inner class Exporter(private val format: PluginManifest.Exporter) : StoryExporter {
		override val formatId = format.format
		override val fileExtension = format.extension
		override val mimeType = format.mime
		override val label = format.label
		override val needsProjectData = false

		override fun render(sink: BufferedSink, input: ExportInput) {
			val prose = format.input == PluginManifest.INPUT_PROSE
			val request = ExportRequest(
				format = formatId,
				projectName = input.projectName,
				language = input.language,
				topLevelAsChapters = input.treatTopLevelAsChapters,
				chapters = input.bookChapters().map { chapter ->
					if (prose) {
						ExportRequest.Chapter(chapter.name, prose = chapter.scenes.map(::proseOf))
					} else {
						ExportRequest.Chapter(chapter.name, scenes = chapter.scenes)
					}
				},
				settings = settingsValues(),
			)
			// Export renders on a background dispatcher already.
			sink.write(callBlocking(EXPORT, OperationJson.encodeToString(request).encodeToByteArray()))
		}
	}

	@Serializable
	private class ActionRequest(
		val action: String,
		val project: String,
		/** The plugin's declared settings, every key present. */
		val settings: JsonObject,
	)

	@Serializable
	private class CommandRequest(
		val command: String,
		val args: List<String>,
		/** The plugin's declared settings, every key present. */
		val settings: JsonObject,
		/** One line of standard input, without its line break. */
		val line: String,
	)

	@Serializable
	private class DispatchRequest(val operation: String, val input: JsonElement = JsonObject(emptyMap()))

	/** Has either [output] or [error], never both. */
	@OptIn(ExperimentalSerializationApi::class)
	@Serializable
	private class DispatchReply(
		@EncodeDefault(EncodeDefault.Mode.NEVER) val output: JsonElement? = null,
		@EncodeDefault(EncodeDefault.Mode.NEVER) val error: Error? = null,
	) {
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
		/**
		 * Whether the story's top-level groups are chapters. When not, [chapters] holds one, named for
		 * the project, with every scene.
		 */
		val topLevelAsChapters: Boolean,
		val chapters: List<Chapter>,
		/** The plugin's declared settings, every key present. */
		val settings: JsonObject,
	) {
		/** Each scene in [scenes] as markdown, or in [prose] as blocks, as the exporter's `input` asks. */
		@Serializable
		class Chapter(
			val name: String,
			val scenes: List<String> = emptyList(),
			val prose: List<List<ExportBlock>> = emptyList(),
		)
	}

	companion object {
		/** The import a module calls operations through, in `extism:host/user`. */
		const val DISPATCH = "hammer_dispatch"

		/** The import that reads the plugin's cache: a key's value, or 0 when it has none. */
		const val CACHE_GET = "hammer_cache_get"

		/** The import that writes the plugin's cache: a key and its value, or 0 (or an empty value) to remove it. */
		const val CACHE_SET = "hammer_cache_set"

		/** The export that renders every export format the manifest declares. */
		const val EXPORT = "export"

		/** The export that answers each line of input to a command the manifest declares. */
		const val COMMAND = "command"

		/** The export that runs a project action the manifest declares; its output is shown to the user. */
		const val ACTION = "action"

		const val PERMISSION_DENIED = "PermissionDenied"

		/** An operation that failed for a reason other than the caller's input, such as Hammer being busy. */
		const val FAILED = "Failed"

		private const val COMMAND_FAILED = 1

		/** Function calls plus loop iterations allowed per call before the module is stopped. */
		const val DEFAULT_FUEL_PER_CALL = 2_000_000_000L

		/** Guest heap a module may keep between calls; a Kotlin/Wasm plugin's small calls need a few MiB. */
		const val DEFAULT_RELEASE_GUEST_HEAP_ABOVE = 64L * 1024 * 1024
	}
}
