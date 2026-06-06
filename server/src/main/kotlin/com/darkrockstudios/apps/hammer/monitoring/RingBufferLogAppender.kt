package com.darkrockstudios.apps.hammer.monitoring

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase

/**
 * Logback appender that mirrors log events into the in-memory [LogRingBuffer]
 * so the admin log viewer can tail them. Instantiated by Logback from
 * `logback.xml`. Best-effort secret redaction is applied before storing.
 */
class RingBufferLogAppender : AppenderBase<ILoggingEvent>() {

	override fun append(event: ILoggingEvent) {
		val message = redactSecrets(event.formattedMessage ?: "")
		LogRingBuffer.add(
			LogLine(
				timestampMillis = event.timeStamp,
				levelRank = LogRingBuffer.rankOf(event.level.levelStr),
				level = event.level.levelStr,
				logger = event.loggerName.substringAfterLast('.'),
				message = message,
			),
		)
	}

	companion object {
		private val REDACTIONS = listOf(
			Regex("(?i)(bearer\\s+)[A-Za-z0-9._\\-]+") to "$1***",
			Regex("(?i)(token[\"'=:\\s]+)[A-Za-z0-9._\\-]+") to "$1***",
			Regex("(?i)(password[\"'=:\\s]+)\\S+") to "$1***",
		)

		fun redactSecrets(message: String): String =
			REDACTIONS.fold(message) { acc, (pattern, replacement) -> pattern.replace(acc, replacement) }
	}
}
