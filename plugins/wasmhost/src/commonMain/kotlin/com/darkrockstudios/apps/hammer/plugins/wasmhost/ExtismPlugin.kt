package com.darkrockstudios.apps.hammer.plugins.wasmhost

import io.github.charlietap.chasm.embedding.remainingFuel
import io.github.charlietap.chasm.embedding.setFuel
import io.github.charlietap.chasm.config.GCStrategy
import io.github.charlietap.chasm.config.RuntimeConfig
import io.github.charlietap.chasm.embedding.dsl.FunctionTypeBuilder
import io.github.charlietap.chasm.embedding.dsl.ValueTypeListBuilder
import io.github.charlietap.chasm.embedding.error.ChasmError
import io.github.charlietap.chasm.embedding.exports
import io.github.charlietap.chasm.embedding.function
import io.github.charlietap.chasm.embedding.global.readGlobal
import io.github.charlietap.chasm.embedding.global.writeGlobal
import io.github.charlietap.chasm.embedding.instance
import io.github.charlietap.chasm.embedding.invoke
import io.github.charlietap.chasm.embedding.module
import io.github.charlietap.chasm.embedding.shapes.ChasmResult
import io.github.charlietap.chasm.embedding.shapes.Global
import io.github.charlietap.chasm.embedding.shapes.Import
import io.github.charlietap.chasm.embedding.shapes.Instance
import io.github.charlietap.chasm.embedding.store
import io.github.charlietap.chasm.host.HostFunction
import io.github.charlietap.chasm.host.HostFunctionException
import io.github.charlietap.chasm.host.HostModuleInstance
import io.github.charlietap.chasm.host.HostParameters
import io.github.charlietap.chasm.host.HostResources
import io.github.charlietap.chasm.host.HostResults
import io.github.charlietap.chasm.host.HostStack
import io.github.charlietap.chasm.host.readI32
import io.github.charlietap.chasm.host.withMemory
import io.github.charlietap.chasm.host.writeI32
import io.github.charlietap.chasm.runtime.value.NumberValue
import io.github.charlietap.chasm.type.FunctionType
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.TimeSource

/**
 * One instance of an Extism-convention plugin on chasm. Not thread-safe: calls run one at a time.
 *
 * Plugins may import only what the host lists here plus [userFunctions]; anything else, such as a
 * WASI file or socket import, fails the load. HTTP imports exist for compatibility and always fail the call.
 */
class ExtismPlugin(
	/**
	 * The module's bytes, asked for once, inside the constructor: held by nothing, they can be collected
	 * as soon as they are instrumented, which matters for a pre-initialized module of 100 MB or more.
	 */
	loadWasm: () -> ByteArray,
	userFunctions: List<UserFunction> = emptyList(),
	private val config: Map<String, String> = emptyMap(),
	private val log: (level: LogLevel, message: String) -> Unit = { _, _ -> },
	instrumenter: FuelInstrumenter = FuelInstrumenter(),
	maxGuestHeapBytes: Long = DEFAULT_MAX_GUEST_HEAP_BYTES,
) {
	constructor(
		wasm: ByteArray,
		userFunctions: List<UserFunction> = emptyList(),
		config: Map<String, String> = emptyMap(),
		log: (level: LogLevel, message: String) -> Unit = { _, _ -> },
		instrumenter: FuelInstrumenter = FuelInstrumenter(),
		maxGuestHeapBytes: Long = DEFAULT_MAX_GUEST_HEAP_BYTES,
	) : this({ wasm }, userFunctions, config, log, instrumenter, maxGuestHeapBytes)

	private val kernel = ExtismKernel()
	private val vars = mutableMapOf<String, ByteArray>()

	// A module using Wasm GC keeps its objects in chasm's heap, not its capped linear memory.
	private val guestHeap = GuestHeap(maxGuestHeapBytes)
	private val store = guestHeap.store ?: store(meterFuel = true)
	private val instance: Instance
	private val fuel: Global
	private var calling = false

	init {
		val module = module(instrumenter.instrument(loadWasm())).orThrow("Invalid plugin module")
		val provided = (envFunctions() + userFunctions.map { it.toHost(kernel) } + RandomGet + ClockTimeGet)
			.associateBy { it.module to it.name }
		val imports = module.imports.map { import ->
			val host = provided[import.moduleName to import.entityName]
				?: throw PluginException("Plugin imports ${import.moduleName} ${import.entityName}, which the host does not provide")
			Import(import.moduleName, import.entityName, function(store, host.type, host))
		}
		// The start function and initializers run on this, as Extism hosts do for reactor modules.
		setFuel(store, FuelInstrumenter.DEFAULT_INSTANTIATION_FUEL)
		instance = instance(store, module, imports, RUNTIME_CONFIG).orThrow("Plugin failed to start")
		fuel = exports(instance).first { it.name == FuelInstrumenter.FUEL_EXPORT }.value as Global
		// Runs on the fuel the instrumenter starts the module with, as Extism hosts do for reactor modules.
		INITIALIZERS.firstOrNull { name -> module.exports.any { it.name == name } }?.let { initializer ->
			invoke(store, instance, initializer).orThrow("Plugin failed to initialize")
		}
	}

	/**
	 * Runs the exported [function] on [input] and returns its output. [fuel] bounds the work done: one
	 * unit per function call or loop iteration.
	 */
	fun call(function: String, input: ByteArray, fuel: Long): ByteArray {
		// A host function re-entering the module would reset the memory and fuel of the call in progress.
		if (calling) throw PluginException("Plugin was called again while running $function")
		calling = true
		try {
			return callOnce(function, input, fuel)
		} finally {
			calling = false
		}
	}

	private fun callOnce(function: String, input: ByteArray, fuel: Long): ByteArray {
		kernel.reset()
		kernel.setInput(input)
		setFuel(store, fuel)

		val result = invoke(store, instance, function)
		if (result is ChasmResult.Error) {
			if (remainingFuel() == 0L) throw PluginException("Plugin ran out of fuel in $function")
			if ((result.error as? ChasmError.ExecutionError)?.error == GUEST_HEAP_EXHAUSTED) {
				throw PluginException("Plugin ran out of memory in $function")
			}
			// A module may set an error and then trap, as AssemblyScript's abort does.
			val reason = reportedError()?.let { "$it (${result.error})" } ?: result.error
			throw PluginException("Plugin failed in $function: $reason")
		}
		val code = ((result as ChasmResult.Success).result.singleOrNull() as? NumberValue.I32)?.value
		if (code != 0) throw PluginException("Plugin reported failure from $function: ${reportedError() ?: "code $code"}")
		return kernel.read(kernel.output, kernel.outputLength)
	}

	private fun reportedError(): String? = kernel.error.takeIf { it != 0L }?.let { kernel.read(it).decodeToString() }

	/**
	 * What the module's Wasm GC objects hold from the host, at the most any call has needed so far: chasm
	 * keeps the pages for reuse. 0 where the heap is not the host's to measure.
	 */
	val guestHeapBytes: Long
		get() = if (guestHeap.store != null) guestHeap.committedBytes else 0

	fun remainingFuel(): Long = remainingFuel(store)

	private fun envFunctions(): List<Host> {
		fun env(name: String, params: String, results: String, body: (Args) -> Long) =
			Host(ENV, name, params, results, body)

		fun text(offset: Long) = kernel.read(offset).decodeToString()
		fun logAt(level: LogLevel): (Args) -> Long = { log(level, text(it[0])); 0 }

		return listOf(
			env("alloc", "I", "I") { kernel.alloc(it[0]) },
			env("free", "I", "") { kernel.free(it[0]); 0 },
			env("length", "I", "I") { kernel.length(it[0]) },
			env("length_unsafe", "I", "I") { kernel.length(it[0]) },
			env("load_u8", "I", "i") { kernel.loadU8(it[0]).toLong() },
			env("load_u64", "I", "I") { kernel.loadU64(it[0]) },
			env("store_u8", "Ii", "") { kernel.storeU8(it[0], it[1].toInt()); 0 },
			env("store_u64", "II", "") { kernel.storeU64(it[0], it[1]); 0 },
			env("input_offset", "", "I") { kernel.input },
			env("input_length", "", "I") { kernel.inputLength() },
			env("input_load_u8", "I", "i") { kernel.inputLoadU8(it[0]).toLong() },
			env("input_load_u64", "I", "I") { kernel.inputLoadU64(it[0]) },
			env("output_set", "II", "") { kernel.outputSet(it[0], it[1]); 0 },
			env("error_set", "I", "") { kernel.errorSet(it[0]); 0 },
			env("config_get", "I", "I") { config[text(it[0])]?.let { value -> kernel.write(value.encodeToByteArray()) } ?: 0 },
			env("var_get", "I", "I") { vars[text(it[0])]?.let(kernel::write) ?: 0 },
			env("var_set", "II", "") {
				val key = text(it[0])
				if (it[1] == 0L) vars.remove(key) else vars[key] = kernel.read(it[1])
				0
			},
			env("log_trace", "I", "", logAt(LogLevel.Trace)),
			env("log_debug", "I", "", logAt(LogLevel.Debug)),
			env("log_info", "I", "", logAt(LogLevel.Info)),
			env("log_warn", "I", "", logAt(LogLevel.Warn)),
			env("log_error", "I", "", logAt(LogLevel.Error)),
			env("get_log_level", "", "i") { LogLevel.Info.ordinal.toLong() },
			env("http_request", "II", "I") { throw HostFunctionException("HTTP is not available to Hammer plugins") },
			env("http_status_code", "", "i") { throw HostFunctionException("HTTP is not available to Hammer plugins") },
			env("http_headers", "", "I") { throw HostFunctionException("HTTP is not available to Hammer plugins") },
		)
	}

	/**
	 * A host function in Extism's `extism:host/user` namespace. Arguments and the result are kernel
	 * offsets: read them with [UserCall.read], return [UserCall.write]'s offset, or 0 for none.
	 */
	class UserFunction(
		val name: String,
		val params: Int,
		val returnsValue: Boolean,
		val body: UserCall.(args: Args) -> Long,
	) {
		internal fun toHost(kernel: ExtismKernel) =
			Host(USER, name, "I".repeat(params), if (returnsValue) "I" else "") { UserCall(kernel).body(it) }
	}

	class UserCall internal constructor(private val kernel: ExtismKernel) {
		fun read(offset: Long): ByteArray = kernel.read(offset)
		fun write(bytes: ByteArray): Long = kernel.write(bytes)
	}

	/** A host call's arguments, read in place from the interpreter's stack. Valid only during the call. */
	class Args internal constructor() {
		internal var stack: LongArray = EMPTY
		internal var base = 0

		operator fun get(index: Int): Long = stack[base + index]

		private companion object {
			val EMPTY = LongArray(0)
		}
	}

	enum class LogLevel { Trace, Debug, Info, Warn, Error }

	internal abstract class Provided(val module: String, val name: String, params: String, results: String) : HostFunction {
		/** `I` is i64 and `i` is i32. */
		val type: FunctionType = FunctionTypeBuilder().apply {
			params { params.forEach { it.valueType(this) } }
			results { results.forEach { it.valueType(this) } }
		}.build()

		private fun Char.valueType(list: ValueTypeListBuilder) {
			if (this == 'I') list.i64() else list.i32()
		}

		/** Turns a failure, such as a pointer outside the plugin's memory, into a trap with [name]. */
		protected inline fun <T> trapping(block: () -> T): T = try {
			block()
		} catch (e: HostFunctionException) {
			throw e
		} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
			throw HostFunctionException("$name: ${e.message}")
		}
	}

	/** A failure in [body] traps the plugin with its message. */
	internal class Host(
		module: String,
		name: String,
		params: String,
		results: String,
		private val body: (Args) -> Long,
	) : Provided(module, name, params, results) {
		private val returnsValue = results.isNotEmpty()
		private val args = Args()

		context(stack: HostStack, module: HostModuleInstance, resources: HostResources)
		override fun invoke(parameters: HostParameters, results: HostResults) {
			args.stack = stack
			args.base = parameters
			val value = try {
				body(args)
			} catch (e: HostFunctionException) {
				throw e
			} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
				throw HostFunctionException("$name: ${e.message}")
			}
			if (returnsValue) stack[results] = value
		}
	}

	/** WASI's randomness, which Kotlin/Wasm's standard library needs. */
	private object RandomGet : Provided("wasi_snapshot_preview1", "random_get", "ii", "i") {
		context(stack: HostStack, module: HostModuleInstance, resources: HostResources)
		override fun invoke(parameters: HostParameters, results: HostResults) {
			val pointer = parameters.readI32(0)
			val bytes = trapping { Random.nextBytes(parameters.readI32(1)) }
			trapping { withMemory(0) { bytes.forEachIndexed { i, byte -> writeI8(pointer + i, byte) } } }
			results.writeI32(0, 0)
		}
	}

	/** WASI's clocks, which kotlinx.serialization on Kotlin/Wasm needs: wall time, and a monotonic time for anything else. */
	private object ClockTimeGet : Provided("wasi_snapshot_preview1", "clock_time_get", "iIi", "i") {
		private val start = TimeSource.Monotonic.markNow()

		context(stack: HostStack, module: HostModuleInstance, resources: HostResources)
		override fun invoke(parameters: HostParameters, results: HostResults) {
			val nanos = if (parameters.readI32(0) == REALTIME) {
				Clock.System.now().let { it.epochSeconds * NANOS_PER_SECOND + it.nanosecondsOfSecond }
			} else {
				start.elapsedNow().inWholeNanoseconds
			}
			val pointer = parameters.readI32(2)
			trapping { withMemory(0) { writeI64(pointer, nanos) } }
			results.writeI32(0, 0)
		}

		private const val REALTIME = 0
		private const val NANOS_PER_SECOND = 1_000_000_000L
	}

	private companion object {
		const val ENV = "extism:host/env"
		const val USER = "extism:host/user"
		val INITIALIZERS = listOf("_initialize", "__wasm_call_ctors")

		// ARENA frees a call's garbage when the call returns. chasm 2.0.0's TRADITIONAL, which collects while
		// a call runs, corrupts live Kotlin/Wasm objects.
		val RUNTIME_CONFIG = RuntimeConfig(gcStrategy = GCStrategy.ARENA)

		/** chasm's error when a module's guest heap is at its cap and collecting frees too little. */
		const val GUEST_HEAP_EXHAUSTED = "GuestHeapOutOfMemory"
	}
}

class PluginException(message: String) : Exception(message)

/**
 * How much a plugin's Wasm GC objects may hold at once. A call holds everything it allocates until it
 * returns; Kotlin/Wasm's style report on a 300,000-word novel needs 768 MiB.
 */
const val DEFAULT_MAX_GUEST_HEAP_BYTES = 1024L * 1024 * 1024

private fun <S> ChasmResult<S, *>.orThrow(context: String): S = when (this) {
	is ChasmResult.Success -> result
	is ChasmResult.Error -> throw PluginException("$context: $error")
}
