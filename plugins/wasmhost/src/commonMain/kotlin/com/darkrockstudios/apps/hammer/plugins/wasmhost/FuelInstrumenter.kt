package com.darkrockstudios.apps.hammer.plugins.wasmhost

/**
 * Rewrites a module so the host can stop it. chasm has no fuel or interrupt of its own, so a mutable
 * i64 global, exported as [FUEL_EXPORT], is decremented on every function entry and loop iteration,
 * and the module traps when it reaches zero. The global starts at [instantiationFuel], which is what
 * a start function and data initialization get. Linear memories are capped at [maxMemoryPages] and
 * tables at [maxTableElements], so growing either cannot exhaust the host's heap.
 */
class FuelInstrumenter(
	private val instantiationFuel: Long = DEFAULT_INSTANTIATION_FUEL,
	private val maxMemoryPages: Int = DEFAULT_MAX_MEMORY_PAGES,
	private val maxTableElements: Int = DEFAULT_MAX_TABLE_ELEMENTS,
) {

	fun instrument(wasm: ByteArray): ByteArray {
		val sections = readSections(wasm)
		val importedGlobals = sections.firstOrNull { it.id == IMPORT }?.let { countImportedGlobals(it.body()) } ?: 0
		val definedGlobals = sections.firstOrNull { it.id == GLOBAL }?.let { Reader(it.body()).u32() } ?: 0
		val fuel = importedGlobals + definedGlobals

		val out = mutableListOf<Section>()
		var addedGlobal = false
		var addedExport = false
		for (section in sections) {
			if (!addedGlobal && section.id != GLOBAL && sectionOrder(section.id) > sectionOrder(GLOBAL)) {
				out += Section(GLOBAL, appendGlobal(null))
				addedGlobal = true
			}
			if (!addedExport && section.id != EXPORT && sectionOrder(section.id) > sectionOrder(EXPORT)) {
				out += Section(EXPORT, appendExport(null, fuel))
				addedExport = true
			}
			out += when (section.id) {
				GLOBAL -> Section(GLOBAL, appendGlobal(section.body())).also { addedGlobal = true }
				EXPORT -> Section(EXPORT, appendExport(section.body(), fuel)).also { addedExport = true }
				TABLE -> Section(TABLE, capTables(section.body()))
				MEMORY -> Section(MEMORY, capMemories(section.body()))
				CODE -> Section(CODE, instrumentCode(section.body(), fuel))
				else -> section
			}
		}
		if (!addedGlobal) out += Section(GLOBAL, appendGlobal(null))
		if (!addedExport) out += Section(EXPORT, appendExport(null, fuel))

		// Sized exactly, so the output is allocated once: a pre-initialized module is mostly its data section.
		val size = HEADER_SIZE + out.sumOf { 1 + u32Size(it.size) + it.size }
		return Writer(size).apply {
			bytes(wasm, 0, HEADER_SIZE)
			out.forEach { section ->
				byte(section.id)
				u32(section.size)
				bytes(section.source, section.offset, section.size)
			}
		}.toByteArray()
	}

	/** A section's body, [size] bytes of [source] from [offset], so an unchanged one is never copied. */
	private class Section(val id: Int, val source: ByteArray, val offset: Int, val size: Int) {
		constructor(id: Int, body: ByteArray) : this(id, body, 0, body.size)

		fun body(): ByteArray = if (offset == 0 && size == source.size) source else source.copyOfRange(offset, offset + size)
	}

	private fun u32Size(value: Int): Int {
		var remaining = value.toLong() and 0xFFFFFFFFL
		var bytes = 1
		while (remaining >= 0x80) {
			remaining = remaining ushr 7
			bytes++
		}
		return bytes
	}

	private fun readSections(wasm: ByteArray): List<Section> {
		require(wasm.size >= HEADER_SIZE && wasm.copyOfRange(0, 4).contentEquals(MAGIC)) { "Not a wasm module" }
		val reader = Reader(wasm, HEADER_SIZE)
		val sections = mutableListOf<Section>()
		while (!reader.atEnd) {
			val id = reader.byte()
			val size = reader.u32()
			sections += Section(id, wasm, reader.pos, size)
			reader.skip(size)
		}
		return sections
	}

	private fun countImportedGlobals(body: ByteArray): Int {
		val reader = Reader(body)
		var globals = 0
		repeat(reader.u32()) {
			reader.name()
			reader.name()
			when (reader.byte()) {
				0x00 -> reader.u32()
				0x01 -> {
					reader.refType()
					reader.limits()
				}
				0x02 -> reader.limits()
				0x03 -> {
					reader.valType()
					reader.byte()
					globals++
				}
				0x04 -> {
					reader.byte()
					reader.u32()
				}
				else -> error("Unknown import kind")
			}
		}
		return globals
	}

	private fun appendGlobal(body: ByteArray?): ByteArray {
		val reader = body?.let(::Reader)
		val count = reader?.u32() ?: 0
		return Writer().apply {
			u32(count + 1)
			if (reader != null) bytes(reader.rest())
			byte(I64)
			byte(MUTABLE)
			byte(I64_CONST)
			s64(instantiationFuel)
			byte(END)
		}.toByteArray()
	}

	private fun appendExport(body: ByteArray?, fuel: Int): ByteArray {
		val reader = body?.let(::Reader)
		val count = reader?.u32() ?: 0
		return Writer().apply {
			u32(count + 1)
			if (reader != null) bytes(reader.rest())
			name(FUEL_EXPORT)
			byte(EXPORT_GLOBAL)
			u32(fuel)
		}.toByteArray()
	}

	private fun capTables(body: ByteArray): ByteArray {
		val reader = Reader(body)
		val count = reader.u32()
		return Writer().apply {
			u32(count)
			repeat(count) {
				// A table with an initializer is prefixed 0x40 0x00 and followed by a constant expression.
				val initialized = reader.source[reader.pos].toInt() == TABLE_WITH_INIT
				if (initialized) {
					reader.skip(2)
					byte(TABLE_WITH_INIT)
					byte(0x00)
				}
				val typeStart = reader.pos
				reader.refType()
				bytes(reader.source, typeStart, reader.pos - typeStart)
				val flags = reader.byte()
				require(flags and SHARED_OR_64 == 0) { "Shared and 64-bit tables are not supported" }
				val min = reader.u32()
				val max = if (flags and HAS_MAX != 0) reader.u32() else null
				require(min <= maxTableElements) { "Module needs a table of $min; the limit is $maxTableElements" }
				byte(HAS_MAX)
				u32(min)
				u32(minOf(max ?: maxTableElements, maxTableElements))
				if (initialized) {
					val exprStart = reader.pos
					while (true) {
						val opcode = reader.byte()
						if (opcode == END) break
						skipImmediates(reader, opcode)
					}
					bytes(reader.source, exprStart, reader.pos - exprStart)
				}
			}
		}.toByteArray()
	}

	private fun capMemories(body: ByteArray): ByteArray {
		val reader = Reader(body)
		val count = reader.u32()
		return Writer().apply {
			u32(count)
			repeat(count) {
				val flags = reader.byte()
				require(flags and SHARED_OR_64 == 0) { "Shared and 64-bit memories are not supported" }
				val min = reader.u32()
				val max = if (flags and HAS_MAX != 0) reader.u32() else null
				require(min <= maxMemoryPages) { "Module needs $min memory pages; the limit is $maxMemoryPages" }
				byte(HAS_MAX)
				u32(min)
				u32(minOf(max ?: maxMemoryPages, maxMemoryPages))
			}
		}.toByteArray()
	}

	private fun instrumentCode(body: ByteArray, fuel: Int): ByteArray {
		val reader = Reader(body)
		val count = reader.u32()
		return Writer().apply {
			u32(count)
			repeat(count) {
				val function = instrumentFunction(Reader(reader.bytes(reader.u32())), fuel)
				u32(function.size)
				bytes(function)
			}
		}.toByteArray()
	}

	private fun instrumentFunction(reader: Reader, fuel: Int): ByteArray {
		val out = Writer()
		val localsStart = reader.pos
		repeat(reader.u32()) {
			reader.u32()
			reader.valType()
		}
		out.bytes(reader.source, localsStart, reader.pos - localsStart)
		out.fuelCheck(fuel)

		while (!reader.atEnd) {
			val start = reader.pos
			val opcode = reader.byte()
			skipImmediates(reader, opcode)
			out.bytes(reader.source, start, reader.pos - start)
			if (opcode == LOOP) out.fuelCheck(fuel)
		}
		return out.toByteArray()
	}

	private fun Writer.fuelCheck(fuel: Int) {
		byte(GLOBAL_GET); u32(fuel)
		byte(I64_EQZ)
		byte(IF); byte(EMPTY_BLOCK)
		byte(UNREACHABLE)
		byte(END)
		byte(GLOBAL_GET); u32(fuel)
		byte(I64_CONST); s64(1)
		byte(I64_SUB)
		byte(GLOBAL_SET); u32(fuel)
	}

	@Suppress("CyclomaticComplexMethod", "MagicNumber")
	private fun skipImmediates(reader: Reader, opcode: Int) {
		when (opcode) {
			0x02, 0x03, 0x04, 0x06 -> reader.blockType()
			0x07, 0x08, 0x09, 0x0C, 0x0D, 0x10, 0x12, 0x14, 0x15, 0x18, 0xD2, 0xD5, 0xD6 -> reader.u32()
			0x0E -> {
				repeat(reader.u32()) { reader.u32() }
				reader.u32()
			}
			0x11, 0x13 -> {
				reader.u32()
				reader.u32()
			}
			0x1C -> repeat(reader.u32()) { reader.valType() }
			0x1F -> {
				reader.blockType()
				repeat(reader.u32()) {
					when (reader.byte()) {
						0x00, 0x01 -> {
							reader.u32()
							reader.u32()
						}
						else -> reader.u32()
					}
				}
			}
			in 0x20..0x26 -> reader.u32()
			in 0x28..0x3E -> reader.memArg()
			0x3F, 0x40 -> reader.u32()
			0x41 -> reader.s64()
			0x42 -> reader.s64()
			0x43 -> reader.skip(4)
			0x44 -> reader.skip(8)
			0xD0 -> reader.s64()
			0xFB -> gcImmediates(reader, reader.u32())
			0xFC -> miscImmediates(reader, reader.u32())
			0xFD, 0xFE -> error("SIMD and threads are not supported")
			else -> require(opcode in NO_IMMEDIATES) { "Unknown opcode 0x${opcode.toString(16)}" }
		}
	}

	@Suppress("MagicNumber")
	private fun gcImmediates(reader: Reader, op: Int) {
		when (op) {
			2, 3, 4, 5, 8, 9, 10, 17, 18, 19 -> {
				reader.u32()
				reader.u32()
			}
			0, 1, 6, 7, 11, 12, 13, 14, 16 -> reader.u32()
			20, 21, 22, 23 -> reader.s64()
			24, 25 -> {
				reader.byte()
				reader.u32()
				reader.s64()
				reader.s64()
			}
			15, in 26..30 -> Unit
			else -> error("Unknown GC opcode $op")
		}
	}

	@Suppress("MagicNumber")
	private fun miscImmediates(reader: Reader, op: Int) {
		when (op) {
			in 0..7, in 19..22 -> Unit
			8, 10, 12, 14 -> {
				reader.u32()
				reader.u32()
			}
			9, 11, 13, 15, 16, 17 -> reader.u32()
			else -> error("Unknown 0xFC opcode $op")
		}
	}

	private class Reader(val source: ByteArray, var pos: Int = 0) {
		val atEnd get() = pos >= source.size

		fun byte(): Int = source[pos++].toInt() and 0xFF

		fun skip(count: Int) {
			pos += count
		}

		fun bytes(count: Int): ByteArray = source.copyOfRange(pos, pos + count).also { pos += count }

		fun rest(): ByteArray = bytes(source.size - pos)

		fun u32(): Int {
			var result = 0L
			var shift = 0
			while (true) {
				val b = byte()
				result = result or ((b and 0x7F).toLong() shl shift)
				if (b and 0x80 == 0) return result.toInt()
				shift += 7
			}
		}

		/** Any signed LEB up to 64 bits; the value itself is never needed. */
		fun s64() {
			while (byte() and 0x80 != 0) Unit
		}

		fun name() = skip(u32())

		fun heapType() = s64()

		fun refType() {
			when (byte()) {
				REF, REF_NULL -> heapType()
			}
		}

		fun valType() = refType()

		fun blockType() {
			val first = source[pos].toInt() and 0xFF
			when {
				first == REF || first == REF_NULL -> {
					pos++
					heapType()
				}
				first and 0xC0 == 0x40 -> pos++
				else -> s64()
			}
		}

		fun limits() {
			val flags = byte()
			u32()
			if (flags and HAS_MAX != 0) u32()
		}

		fun memArg() {
			val align = u32()
			if (align and MEMARG_HAS_MEMORY != 0) u32()
			u32()
		}
	}

	private class Writer(capacity: Int = 256) {
		private var buffer = ByteArray(capacity)
		private var size = 0

		fun byte(value: Int) {
			ensure(1)
			buffer[size++] = value.toByte()
		}

		fun bytes(source: ByteArray, offset: Int = 0, count: Int = source.size - offset) {
			ensure(count)
			source.copyInto(buffer, size, offset, offset + count)
			size += count
		}

		fun u32(value: Int) {
			var remaining = value.toLong() and 0xFFFFFFFFL
			do {
				var b = (remaining and 0x7F).toInt()
				remaining = remaining ushr 7
				if (remaining != 0L) b = b or 0x80
				byte(b)
			} while (remaining != 0L)
		}

		fun s64(value: Long) {
			var remaining = value
			while (true) {
				val b = (remaining and 0x7F).toInt()
				remaining = remaining shr 7
				val done = (remaining == 0L && b and 0x40 == 0) || (remaining == -1L && b and 0x40 != 0)
				byte(if (done) b else b or 0x80)
				if (done) return
			}
		}

		fun name(value: String) {
			val encoded = value.encodeToByteArray()
			u32(encoded.size)
			bytes(encoded)
		}

		fun toByteArray(): ByteArray = if (size == buffer.size) buffer else buffer.copyOf(size)

		private fun ensure(extra: Int) {
			if (size + extra > buffer.size) buffer = buffer.copyOf(maxOf(buffer.size * 2, size + extra))
		}
	}

	companion object {
		const val FUEL_EXPORT = "__hammer_fuel"

		const val DEFAULT_INSTANTIATION_FUEL = 10_000_000L

		const val DEFAULT_MAX_TABLE_ELEMENTS = 100_000

		/** [PluginManifest.DEFAULT_MEMORY_MIB]. */
		const val DEFAULT_MAX_MEMORY_PAGES = PluginManifest.DEFAULT_MEMORY_MIB * 16

		private val MAGIC = byteArrayOf(0x00, 0x61, 0x73, 0x6D)
		private const val HEADER_SIZE = 8

		private const val IMPORT = 2
		private const val TABLE = 4
		private const val MEMORY = 5
		private const val GLOBAL = 6
		private const val EXPORT = 7
		private const val CODE = 10
		private const val TAG = 13
		private const val DATA_COUNT = 12

		// Section ids are not in file order: tag sits between memory and global, data count before code.
		private fun sectionOrder(id: Int): Int = when (id) {
			0 -> -1
			TAG -> 55
			DATA_COUNT -> 95
			else -> id * 10
		}

		private const val EXPORT_GLOBAL = 0x03
		private const val TABLE_WITH_INIT = 0x40
		private const val HAS_MAX = 0x01
		private const val SHARED_OR_64 = 0x06
		private const val MEMARG_HAS_MEMORY = 0x40

		private const val UNREACHABLE = 0x00
		private const val LOOP = 0x03
		private const val IF = 0x04
		private const val END = 0x0B
		private const val GLOBAL_GET = 0x23
		private const val GLOBAL_SET = 0x24
		private const val I64_CONST = 0x42
		private const val I64_EQZ = 0x50
		private const val I64_SUB = 0x7D
		private const val EMPTY_BLOCK = 0x40
		private const val I64 = 0x7E
		private const val MUTABLE = 0x01
		private const val REF = 0x64
		private const val REF_NULL = 0x63

		private val NO_IMMEDIATES: Set<Int> =
			setOf(0x00, 0x01, 0x05, 0x0A, 0x0B, 0x0F, 0x19, 0x1A, 0x1B, 0xD1, 0xD3, 0xD4) + (0x45..0xC4)
	}
}
