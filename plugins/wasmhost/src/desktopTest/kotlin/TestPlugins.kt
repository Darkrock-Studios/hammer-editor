/** Test plugins built from `testplugins/` by its build scripts. */
fun testPlugin(name: String): ByteArray =
	requireNotNull(object {}.javaClass.getResourceAsStream("/plugins/$name.wasm")) { "No test plugin $name" }
		.use { it.readBytes() }

fun Long.toLittleEndian(): ByteArray = ByteArray(8) { (this ushr (8 * it)).toByte() }

/** A wasm module's magic number and version, before any sections. */
val MODULE_HEADER = byteArrayOf(0x00, 0x61, 0x73, 0x6D, 0x01, 0x00, 0x00, 0x00)

/** 2^31, as an unsigned LEB128. */
val U32_2_POW_31 = byteArrayOf(0x80.toByte(), 0x80.toByte(), 0x80.toByte(), 0x80.toByte(), 0x08)
