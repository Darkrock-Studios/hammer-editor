package de.jonasbroeckmann.kzip

internal class Crc32 {
	private var crc: Int = -1

	fun update(data: ByteArray, offset: Int = 0, length: Int = data.size) {
		var c = crc
		for (i in offset until offset + length) {
			c = table[(c xor data[i].toInt()) and 0xFF] xor (c ushr 8)
		}
		crc = c
	}

	fun update(byte: Int) {
		crc = table[(crc xor byte) and 0xFF] xor (crc ushr 8)
	}

	fun getValue(): Long = crc.toLong().inv() and 0xFFFFFFFFL

	fun reset() {
		crc = -1
	}

	companion object {
		private val table = IntArray(256) {
			var c = it
			for (k in 0 until 8) {
				c = if (c and 1 != 0) -0x12477ce0 xor (c ushr 1) else c ushr 1
			}
			c
		}

		fun calculate(data: ByteArray): Long {
			val crc = Crc32()
			crc.update(data)
			return crc.getValue()
		}
	}
}
