package com.darkrockstudios.apps.hammer.plugins.wasmhost

import com.darkrockstudios.apps.hammer.base.image.ImageFormat
import com.darkrockstudios.apps.hammer.common.data.export.ExportImage
import com.darkrockstudios.apps.hammer.common.data.export.ExportInput
import com.darkrockstudios.apps.hammer.common.data.export.StoryExporter
import com.darkrockstudios.apps.hammer.common.dependencyinjection.injectIoDispatcher
import com.darkrockstudios.apps.hammer.common.util.zip.InMemoryZipEntry
import com.darkrockstudios.apps.hammer.common.util.zip.writeZip
import com.darkrockstudios.apps.hammer.operations.Base64Bytes
import com.darkrockstudios.apps.hammer.operations.OperationException
import com.darkrockstudios.apps.hammer.operations.OperationJson
import com.darkrockstudios.apps.hammer.operations.OperationRegistry
import com.darkrockstudios.apps.hammer.operations.PLUGIN_WITHHELD_OPERATIONS
import com.darkrockstudios.apps.hammer.operations.cli.CliCommand
import com.darkrockstudios.apps.hammer.operations.cli.CliIo
import com.darkrockstudios.apps.hammer.operations.cli.Dispatcher
import com.darkrockstudios.apps.hammer.operations.core.OPS_LIST
import com.darkrockstudios.apps.hammer.operations.core.OperationDescriptor
import com.darkrockstudios.apps.hammer.operations.core.OperationList
import com.darkrockstudios.apps.hammer.operations.core.descriptor
import com.darkrockstudios.apps.hammer.operations.plugin.ActionCall
import com.darkrockstudios.apps.hammer.operations.plugin.ActionCancelledException
import com.darkrockstudios.apps.hammer.operations.plugin.ActionPlace
import com.darkrockstudios.apps.hammer.operations.plugin.ActionProgress
import com.darkrockstudios.apps.hammer.operations.plugin.ActionReply
import com.darkrockstudios.apps.hammer.operations.plugin.ActionTarget
import com.darkrockstudios.apps.hammer.operations.plugin.ClientPlugin
import com.darkrockstudios.apps.hammer.operations.plugin.PageTable
import com.darkrockstudios.apps.hammer.operations.plugin.PluginAction
import com.darkrockstudios.apps.hammer.operations.plugin.PluginRegistry
import com.darkrockstudios.apps.hammer.operations.plugin.SettingDeclaration
import com.darkrockstudios.apps.hammer.operations.plugin.TextDiagnostic
import com.darkrockstudios.apps.hammer.operations.plugin.TextDiagnosticSeverity
import com.darkrockstudios.apps.hammer.operations.plugin.TextDiagnosticsProvider
import com.darkrockstudios.apps.hammer.operations.plugin.TextFix
import com.darkrockstudios.apps.hammer.operations.plugin.toActionFields
import io.github.aakira.napier.Napier
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.booleanOrNull
import okio.BufferedSink
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import kotlin.io.encoding.Base64

/**
 * A runtime plugin: a manifest plus an Extism-convention module, presented as a [ClientPlugin].
 * The module is instantiated on first use, and calls into it run one at a time. It may dispatch only
 * operations covered by an [OperationGrant] that its manifest requests and the user [granted], never one
 * [com.darkrockstudios.apps.hammer.operations.PLUGIN_WITHHELD_OPERATIONS] lists, and `ops.list`
 * shows it only those.
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
	/** Reads a file from the package's `resources/` for [RESOURCE], or null when there is none by that name. */
	private val readResource: (String) -> ByteArray? = { null },
	private val fuelPerCall: Long = DEFAULT_FUEL_PER_CALL,
	private val releaseGuestHeapAbove: Long = DEFAULT_RELEASE_GUEST_HEAP_ABOVE,
	/** The UI's BCP 47 tag, sent with every call so a plugin can reply in it. */
	private val locale: String? = null,
	private val httpClient: PluginHttp? = platformPluginHttp,
	/** What the module's kernel may hold, which a small host heap lowers; see [kernelCapacity]. */
	private val maxKernelBytes: Long = DEFAULT_MAX_KERNEL_BYTES,
) : ClientPlugin, KoinComponent {

	override val id: String = manifest.id
	override val name: String = manifest.name

	private val grants: List<OperationGrant> =
		manifest.permissions.operations.filter { it in granted }.mapNotNull(OperationGrant::parse)

	// Checked here as well as in PluginPackage, for a grant that never went through its checks.
	private fun permits(op: OperationDescriptor) = op.name !in PLUGIN_WITHHELD_OPERATIONS && grants.any { it.covers(op) }

	private val lock = reentrantLock()

	// Only touched while holding the lock.
	private var loaded: ExtismPlugin? = null
	private val module: ExtismPlugin
		get() = loaded ?: ExtismPlugin(
			loadModule,
			listOf(dispatchFunction(), progressFunction(), resourceFunction()) + cacheFunctions(),
			log = ::log,
			limiter = ModuleLimiter(maxMemoryPages = manifest.limits.memory * PAGES_PER_MIB),
			http = pluginHttp(),
			maxKernelBytes = maxKernelBytes,
		).also { loaded = it }

	// The call [interruptibly] is running, if any, which cancelling its caller interrupts. Only touched
	// while holding interruptLock, which a call clears before the lock can pass to the next.
	private val interruptLock = reentrantLock()
	private var running: Running? = null

	private class Running(val module: ExtismPlugin)

	private val appRoute by lazy { AppRoute(get()) }

	// The route of the call running now; only touched while holding the lock.
	private var activeRoute: Route? = null

	// The action run in progress, which progress reports go to, and whether it was cancelled; only
	// touched while holding the lock.
	private var activeAction: ActionCall? = null
	private var actionCancelled = false

	private val ioDispatcher by injectIoDispatcher()

	override fun exporters(): List<StoryExporter> = manifest.exporters.map(::Exporter)

	override fun settings(): List<SettingDeclaration> = declaredSettings

	override fun cliCommands(): List<CliCommand> = manifest.commands.map(::Command)

	override fun actions(): List<PluginAction> = manifest.actions.map { action ->
		PluginAction(
			pluginId = id,
			name = action.name,
			label = action.label,
			places = action.places.mapNotNull(ActionPlace::of).toSet(),
			fields = action.field.toActionFields(),
			keep = action.keep,
			pluginVersion = manifest.version,
		) { call -> runAction(action.name, call) }
	}

	private suspend fun runAction(name: String, call: ActionCall): ActionReply {
		val request = ActionRequest(
			action = name,
			project = call.project,
			settings = settingsValues(),
			locale = locale,
			context = ActionRequest.Context(call.place.id, call.itemId),
			input = call.input,
			button = call.button,
			changed = call.changed,
			state = call.state,
			resumed = call.resumed,
		)
		val output = interruptibly {
			activeAction = call
			actionCancelled = false
			try {
				callBlocking(ACTION, OperationJson.encodeToString(request).encodeToByteArray())
			} catch (e: PluginException) {
				if (actionCancelled) throw ActionCancelledException()
				throw e
			} finally {
				activeAction = null
			}
		}
		return try {
			actionReply(output)
		} catch (e: IllegalArgumentException) {
			throw PluginException("Plugin '$id' sent a malformed reply from action '$name': ${e.message}")
		}
	}

	/** Throws [IllegalArgumentException] for a reply that is not one, and [PluginException] for state too large to keep. */
	private fun actionReply(output: ByteArray): ActionReply {
		val text = output.decodeToString()
		if (text.isBlank()) return ActionReply()
		val reply = ReplyJson.decodeFromString<ActionReplyJson>(text)
		val page = reply.page?.let { page ->
			// The state is part of the reply, so only a reply this large can hold too much.
			val state = page.state
			if (output.size > MAX_STATE_BYTES && state != null) {
				val size = OperationJson.encodeToString(JsonElement.serializer(), state).encodeToByteArray().size
				if (size > MAX_STATE_BYTES) throw PluginException("Plugin '$id' sent a page state of $size bytes, over $MAX_STATE_BYTES")
			}
			page.toActionPage()
		}
		val open = reply.open?.let { open ->
			ActionTarget(ActionPlace.of(open.place) ?: throw IllegalArgumentException("unknown place '${open.place}' to open"), open.id)
		}
		return ActionReply(page = page, message = reply.message?.ifBlank { null }, errors = reply.errors, open = open)
	}

	override fun textDiagnostics(): List<TextDiagnosticsProvider> = manifest.diagnostics.map { check ->
		TextDiagnosticsProvider(check.label, wholeScene = check.scope == PluginManifest.SCOPE_SCENE) { text ->
			val paragraphs = text.paragraphs
			val request = DiagnoseRequest(check.name, paragraphs, text.language, text.project, settingsValues(), locale)
			val found = List(paragraphs.size) { mutableListOf<TextDiagnostic>() }
			try {
				val reply = call(DIAGNOSE, OperationJson.encodeToString(request).encodeToByteArray()).decodeToString()
				if (reply.isBlank()) return@TextDiagnosticsProvider found
				ReplyJson.decodeFromString<DiagnoseReply>(reply).diagnostics.forEach { item ->
					val paragraph = paragraphs.getOrNull(item.paragraph) ?: return@forEach
					val start = utf16Offset(paragraph, item.start) ?: return@forEach
					val end = utf16Offset(paragraph, item.end) ?: return@forEach
					if (start < end) {
						found[item.paragraph] += TextDiagnostic(start, end, item.message, item.fixes.mapNotNull(::textFix), severity(item.severity))
					}
				}
			} catch (e: PluginException) {
				Napier.w(e) { "Plugin '$id' could not check text" }
			} catch (e: SerializationException) {
				Napier.w(e) { "Plugin '$id' sent a malformed diagnose reply" }
			}
			found
		}
	}

	/** Loads the module now rather than on first use, which surfaces any problem with it as a [PluginException]. */
	fun instantiate() {
		lock.withLock { module }
	}

	/** Runs the module's [function] on [input], off the caller's thread; cancelling the caller stops it at once. */
	suspend fun call(function: String, input: ByteArray): ByteArray = interruptibly { callBlocking(function, input) }

	/**
	 * Runs [block], which calls the module, holding the lock on the IO dispatcher. Cancelling the caller
	 * interrupts the module, which otherwise would run to the end of its call.
	 */
	private suspend fun <T> interruptibly(block: () -> T): T = coroutineScope {
		var mine: Running? = null
		val work = async(ioDispatcher) {
			lock.withLock {
				ensureActive()
				val outer = interruptLock.withLock { running.also { running = Running(module).also { mine = it } } }
				try {
					block()
				} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
					// A call interrupted because its caller was cancelled ends as that cancellation, not a failure.
					if (!isActive) Napier.d(e) { "Plugin '$id' call ended after its caller was cancelled" }
					ensureActive()
					throw e
				} finally {
					interruptLock.withLock { running = outer }
				}
			}
		}
		try {
			work.await()
		} catch (e: CancellationException) {
			interruptLock.withLock { running?.takeIf { it === mine }?.module?.interrupt() }
			throw e
		}
	}

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
			// loaded afresh when next used, as is one that ran the host out of memory.
			if (outer == null && loaded?.let { it.outOfMemory || it.guestHeapBytes > releaseGuestHeapAbove } == true) {
				loaded = null
			}
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

	// Reports go to the action running, if any; a cancelled one is stopped here, by trapping.
	private fun progressFunction() = ExtismPlugin.UserFunction(PROGRESS, params = 1, returnsValue = false) { args ->
		val call = activeAction ?: return@UserFunction 0
		if (call.cancelled()) {
			actionCancelled = true
			throw IllegalStateException("Cancelled")
		}
		val report = try {
			ReplyJson.decodeFromString<ProgressReport>(read(args[0]).decodeToString())
		} catch (e: SerializationException) {
			null
		} catch (e: IllegalArgumentException) {
			null
		}
		report?.let { call.onProgress(ActionProgress(it.fraction?.coerceIn(0f, 1f), it.message?.ifBlank { null })) }
		0
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

	private fun resourceFunction() = ExtismPlugin.UserFunction(RESOURCE, params = 1, returnsValue = true) { args ->
		readResource(read(args[0]).decodeToString())?.let(::write) ?: 0
	}

	// Called from inside the module, so it blocks this call's thread until the operation finishes.
	private fun dispatch(request: DispatchRequest): DispatchReply = runBlocking {
		val route = checkNotNull(activeRoute) { "Dispatch outside a call" }
		try {
			val op = route.find(request.operation)
				?: return@runBlocking DispatchReply.failed(OperationException.Kind.NotFound.name, "No operation named '${request.operation}'")
			if (!permits(op)) {
				return@runBlocking DispatchReply.failed(PERMISSION_DENIED, "Plugin '$id' has no permission for ${op.name}")
			}
			val output = if (op.name == OPS_LIST) {
				val permitted = route.operations().filter(::permits)
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
				val request = CommandRequest(name, args, savedSettings(), line, locale)
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

	private fun pluginHttp(): PluginHttp? {
		val client = httpClient ?: return null
		if (manifest.permissions.http.isEmpty() && manifest.permissions.userHosts == null) return null
		return PluginHttp { request ->
			refusal(request.url, manifest.permissions.http, enteredHost())?.let(::error)
			client.send(request)
		}
	}

	// Read on every request, so a change to the setting or its toggle takes effect at once.
	private fun enteredHost(): String? {
		val userHosts = manifest.permissions.userHosts ?: return null
		val settings = settingsValues()
		val allowed = userHosts.toggle?.let { settings.chosen(it)?.booleanOrNull == true } ?: true
		return settings.chosen(userHosts.setting)?.contentOrNull?.takeIf { allowed }?.let(::httpHost)
	}

	// The value of [key] unless it is the declared default, which the package chose rather than the user.
	private fun JsonObject.chosen(key: String): JsonPrimitive? {
		val declared = declaredSettings.firstOrNull { it.key == key } ?: return null
		return (this[key] as? JsonPrimitive)?.takeIf { it != declared.default }
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
		override val needsProjectData = true
		override val embedsImages = format.images
		override val embedsCover = format.cover

		override fun render(sink: BufferedSink, input: ExportInput) {
			val prose = format.input == PluginManifest.INPUT_PROSE
			val images = if (format.images) input.images.values.sortedBy { it.id }.map(ExportRequest::Image) else emptyList()
			val delivered = images.mapTo(mutableSetOf()) { it.id }
			val request = ExportRequest(
				format = formatId,
				projectName = input.projectName,
				language = input.language,
				topLevelAsChapters = input.treatTopLevelAsChapters,
				chapters = input.bookChapters().map { chapter ->
					if (prose) {
						ExportRequest.Chapter(
							chapter.name,
							prose = chapter.scenes.map { proseOf(it, delivered, input.imageNames::get) },
						)
					} else {
						ExportRequest.Chapter(chapter.name, scenes = chapter.scenes)
					}
				},
				images = images,
				coverImageId = input.projectData?.coverImageId?.takeIf { format.cover && it in delivered },
				settings = settingsValues(),
				locale = locale,
			)
			val encoded = OperationJson.encodeToString(request).encodeToByteArray()
			val capacity = kernelCapacity(maxKernelBytes)
			if (encoded.size > capacity) {
				val what = if (images.isEmpty()) "The story is" else "The story's images are"
				throw PluginException(
					"$what too large for export format '$formatId' on this device: its request takes " +
						"${encoded.size} bytes, and plugin '$id' can be sent at most $capacity"
				)
			}
			// Export renders on a background dispatcher already.
			val reply = callBlocking(EXPORT, encoded)
			if (format.output == PluginManifest.OUTPUT_ARCHIVE) writeZip(sink, archiveEntries(reply)) else sink.write(reply)
		}

		// Checks the whole reply before any of it is written, so a refused one leaves the sink untouched.
		private fun archiveEntries(reply: ByteArray): List<InMemoryZipEntry> {
			val files = try {
				ReplyJson.decodeFromString<ArchiveReply>(reply.decodeToString()).files
			} catch (e: IllegalArgumentException) {
				throw PluginException("Plugin '$id' sent a malformed reply from export format '$formatId': ${e.message}")
			}
			fun refuse(problem: String): Nothing =
				throw PluginException("Plugin '$id' sent an invalid archive from export format '$formatId': $problem")
			if (files.isEmpty()) refuse("An archive needs at least one file")
			val paths = hashSetOf<String>()
			return files.map { file ->
				val path = file.path
				val pathBytes = path.encodeToByteArray()
				if (pathBytes.size > MAX_ARCHIVE_PATH_BYTES) {
					refuse("Archive path \"${path.take(QUOTED_LONG_PATH_CHARS)}...\" is longer than $MAX_ARCHIVE_PATH_BYTES bytes")
				}
				archivePathRefusal(path)?.let { refuse("Archive path \"$path\" $it") }
				// An unpaired surrogate encodes as ?, so two such paths could otherwise pass as different and zip as one name.
				if (pathBytes.decodeToString() != path) refuse("Archive path \"$path\" is not valid Unicode")
				if (!paths.add(path)) refuse("Archive path \"$path\" is already in the archive")
				val text = file.text
				val content = file.content
				when {
					text != null && content != null -> refuse("Archive path \"$path\" has both text and content")
					text != null -> InMemoryZipEntry(path, text.encodeToByteArray())
					content != null -> {
						val bytes = try {
							Base64.decode(content)
						} catch (_: IllegalArgumentException) {
							refuse("Archive path \"$path\" has content that is not standard padded base64")
						}
						// Usually an image, which deflating would not shrink.
						InMemoryZipEntry(path, bytes, compress = false)
					}
					else -> refuse("Archive path \"$path\" has neither text nor content")
				}
			}
		}
	}

	// The rules the plugin kits check before sending an archive, worded as they word them.
	private fun archivePathRefusal(path: String): String? = when {
		path.isEmpty() -> "is empty"
		path.startsWith('/') -> "starts with /"
		'\\' in path -> "has a backslash"
		else -> path.split('/').firstNotNullOfOrNull { segment ->
			when (segment) {
				"" -> "has an empty segment"
				".", ".." -> "has a $segment segment"
				else -> null
			}
		}
	}

	/**
	 * What an `archive` exporter replies: [files] in the order to zip them, each with exactly one of
	 * [File.text], written as UTF-8, or [File.content], its bytes as standard padded base64.
	 */
	@Serializable
	private class ArchiveReply(val files: List<File>) {
		@Serializable
		class File(val path: String, val text: String? = null, val content: String? = null)
	}

	@Serializable
	private class ActionRequest(
		val action: String,
		val project: String,
		/** The plugin's declared settings, every key present. */
		val settings: JsonObject,
		/** The UI's BCP 47 tag, or null when Hammer does not know it. */
		val locale: String?,
		val context: Context,
		/** The values of the page the user was on, every key present; empty for a page without fields. */
		val input: JsonObject,
		/** The id of the button pressed; null on the first call. */
		val button: String?,
		val changed: String?,
		/** The last page's state, untouched; null on the first call. */
		val state: JsonElement?,
		val resumed: Boolean,
	) {
		/** The screen the action was run from, and the id of the item it shows there. */
		@Serializable
		class Context(val place: String, val id: Int?)
	}

	/** An action's output, every key optional; empty output is the same as `{}`. */
	@Serializable
	private class ActionReplyJson(
		val page: PageTable? = null,
		val message: String? = null,
		val errors: Map<String, String> = emptyMap(),
		val open: Open? = null,
	) {
		@Serializable
		class Open(val place: String, val id: Int? = null)
	}

	/** What `hammer_progress` is given: how far along, from 0 to 1 if known, and a line saying what it is doing. */
	@Serializable
	private class ProgressReport(val fraction: Float? = null, val message: String? = null)

	@Serializable
	private class DiagnoseRequest(
		val diagnostics: String,
		/** Plain text, without markdown. */
		val paragraphs: List<String>,
		/** The project's BCP 47 tag, or null when it has none. */
		val language: String?,
		val project: String,
		/** The plugin's declared settings, every key present. */
		val settings: JsonObject,
		/** The UI's BCP 47 tag, or null when Hammer does not know it. */
		val locale: String?,
	)

	/**
	 * Offsets are UTF-8 byte offsets into the paragraph, which is what C and Rust index by. A fix is a
	 * replacement, or `{"replacement", "label"}` where the replacement alone would not say what it does.
	 * Severity is `error`, the default, or `suggestion`.
	 */
	@Serializable
	private class DiagnoseReply(val diagnostics: List<Item> = emptyList()) {
		@Serializable
		class Item(
			val paragraph: Int,
			val start: Int,
			val end: Int,
			val message: String,
			val fixes: List<JsonElement> = emptyList(),
			val severity: String? = null,
		)
	}

	private fun severity(name: String?) =
		if (name == SEVERITY_SUGGESTION) TextDiagnosticSeverity.Suggestion else TextDiagnosticSeverity.Error

	private fun textFix(fix: JsonElement): TextFix? = when (fix) {
		is JsonPrimitive -> fix.contentOrNull?.takeIf { fix.isString }?.let(::TextFix)
		is JsonObject -> (fix["replacement"] as? JsonPrimitive)?.takeIf { it.isString }?.content?.let { replacement ->
			TextFix(replacement, (fix["label"] as? JsonPrimitive)?.contentOrNull ?: replacement)
		}
		else -> null
	}

	@Serializable
	private class CommandRequest(
		val command: String,
		val args: List<String>,
		/** The plugin's declared settings, every key present. */
		val settings: JsonObject,
		/** One line of standard input, without its line break. */
		val line: String,
		/** The UI's BCP 47 tag, or null when Hammer does not know it. */
		val locale: String?,
	)

	@Serializable
	private class DispatchRequest(val operation: String, val input: JsonElement = JsonObject(emptyMap()))

	/**
	 * Has either [output] or [error], never both, and no other key: kits take the output's text out of
	 * the reply without parsing it.
	 */
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
		/** The images the story shows, and the cover, by id; empty unless the exporter asks for `images`. */
		val images: List<Image>,
		/** The cover's id in [images]; null unless the exporter asks for the `cover` and its file was read. */
		val coverImageId: Int?,
		/** The plugin's declared settings, every key present. */
		val settings: JsonObject,
		/** The UI's BCP 47 tag, or null when Hammer does not know it. */
		val locale: String?,
	) {
		/** `image.get`'s result plus [mimeType], [content] encoded the same way: standard, padded base64. */
		@Serializable
		class Image(
			val id: Int,
			val name: String,
			val extension: String,
			val mimeType: String,
			@Serializable(with = Base64Bytes::class)
			val content: ByteArray,
		) {
			constructor(image: ExportImage) : this(
				image.id,
				image.name,
				image.fileExtension,
				ImageFormat.mimeType(image.fileExtension) ?: UNKNOWN_MIME_TYPE,
				image.bytes,
			)
		}

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

		/** The import an action reports its progress through, in `extism:host/user`; see [ProgressReport]. */
		const val PROGRESS = "hammer_progress"

		/** The import that reads the plugin's cache: a key's value, or 0 when it has none. */
		const val CACHE_GET = "hammer_cache_get"

		/** The import that writes the plugin's cache: a key and its value, or 0 (or an empty value) to remove it. */
		const val CACHE_SET = "hammer_cache_set"

		/**
		 * The import that reads a file from the package's `resources/`, by its path there: its bytes, or 0
		 * when there is none, or it is empty.
		 */
		const val RESOURCE = "hammer_resource"

		/** The export that renders every export format the manifest declares. */
		const val EXPORT = "export"

		/** The export that answers each line of input to a command the manifest declares. */
		const val COMMAND = "command"

		/** The export that runs an action the manifest declares; its output is shown to the user. */
		const val ACTION = "action"

		/** The most a page's state may take, encoded, as for a cache value. */
		const val MAX_STATE_BYTES = PluginCache.MAX_VALUE_BYTES

		/** The export that checks paragraphs for a text diagnostics check the manifest declares. */
		const val DIAGNOSE = "diagnose"

		private val ReplyJson = Json { ignoreUnknownKeys = true }
		private const val SEVERITY_SUGGESTION = "suggestion"
		private const val UNKNOWN_MIME_TYPE = "application/octet-stream"

		/** The zip format's limit on an entry name, in UTF-8 bytes. */
		private const val MAX_ARCHIVE_PATH_BYTES = 65535

		private const val QUOTED_LONG_PATH_CHARS = 64

		const val PERMISSION_DENIED = "PermissionDenied"

		/** An operation that failed for a reason other than the caller's input, such as Hammer being busy. */
		const val FAILED = "Failed"

		private const val COMMAND_FAILED = 1

		private const val PAGES_PER_MIB = 16

		/** Function calls plus loop iterations allowed per call before the module is stopped. */
		const val DEFAULT_FUEL_PER_CALL = 2_000_000_000L

		/** Guest heap a module may keep between calls; a Kotlin/Wasm plugin's small calls need a few MiB. */
		const val DEFAULT_RELEASE_GUEST_HEAP_ABOVE = 64L * 1024 * 1024
	}
}
