/** Test plugins built from `testplugins/` by its build scripts. */
fun testPlugin(name: String): ByteArray =
	requireNotNull(object {}.javaClass.getResourceAsStream("/plugins/$name.wasm")) { "No test plugin $name" }
		.use { it.readBytes() }

fun Long.toLittleEndian(): ByteArray = ByteArray(8) { (this ushr (8 * it)).toByte() }
