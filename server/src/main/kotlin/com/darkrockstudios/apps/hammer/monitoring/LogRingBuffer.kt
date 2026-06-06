package com.darkrockstudios.apps.hammer.monitoring

/**
 * Bounded, in-memory ring buffer of recent log lines, written by
 * [RingBufferLogAppender] and read by the admin log viewer. Process-global (the
 * appender is instantiated by Logback, outside Koin), thread-safe, and capped so
 * it can never grow without bound.
 */
object LogRingBuffer {
	const val CAPACITY = 10_000

	/** Severity names in ascending order; index is the rank used for filtering. */
	val LEVELS = listOf("TRACE", "DEBUG", "INFO", "WARN", "ERROR")

	private val lock = Any()
	private val buffer = ArrayDeque<LogLine>(CAPACITY)

	fun rankOf(level: String): Int = LEVELS.indexOf(level).let { if (it < 0) 0 else it }

	fun add(line: LogLine) {
		synchronized(lock) {
			buffer.addLast(line)
			while (buffer.size > CAPACITY) buffer.removeFirst()
		}
	}

	/**
	 * Most recent lines (oldest first, newest last) at or above [minLevel],
	 * optionally containing [query] in the message or logger.
	 */
	fun recent(minLevel: String? = null, query: String? = null, limit: Int = 250): List<LogLine> {
		val minRank = if (minLevel.isNullOrBlank()) 0 else rankOf(minLevel)
		val needle = query?.takeIf { it.isNotBlank() }?.lowercase()
		return synchronized(lock) {
			buffer.filter { line ->
				line.levelRank >= minRank &&
					(needle == null || line.message.lowercase().contains(needle) || line.logger.lowercase().contains(needle))
			}.takeLast(limit)
		}
	}

	fun clear() = synchronized(lock) { buffer.clear() }
}

data class LogLine(
	val timestampMillis: Long,
	val levelRank: Int,
	val level: String,
	val logger: String,
	val message: String,
)
