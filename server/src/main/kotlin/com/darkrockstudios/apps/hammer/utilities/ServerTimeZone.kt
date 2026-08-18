package com.darkrockstudios.apps.hammer.utilities

import ch.qos.logback.classic.BasicConfigurator
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.util.ContextInitializer
import org.slf4j.LoggerFactory
import java.time.DateTimeException
import java.time.ZoneId
import java.util.TimeZone

const val TIMEZONE_ENV_VAR = "HAMMER_TIMEZONE"

private val timeZoneLogger = LoggerFactory.getLogger("HammerServer")

/**
 * The zone every server-rendered timestamp and log line is stamped in: [configured] (`timezone` in
 * `config.toml`) wins, then the `HAMMER_TIMEZONE` environment variable, then `TZ`, then the host's
 * own zone. The JVM already honors `TZ` on Linux; reading it back here extends it to hosts that do
 * not, so the same variable works everywhere.
 *
 * An unusable `timezone` or `HAMMER_TIMEZONE` throws, because both are deliberate settings and
 * ignoring one would leave every timestamp wrong with nothing to point at. A `TZ` java.time cannot
 * parse only warns and falls through to the host zone: the POSIX form (`CET-1CEST,M3.5.0`) is a
 * legitimate value the platform has already applied, so it is not ours to reject.
 */
fun resolveServerTimeZone(
	configured: String?,
	readEnv: (String) -> String? = System::getenv,
): ZoneId {
	configured?.trim()?.ifBlank { null }?.let { return parseZone(it, "timezone in config.toml") }
	readEnv(TIMEZONE_ENV_VAR)?.trim()?.ifBlank { null }?.let { return parseZone(it, TIMEZONE_ENV_VAR) }
	readEnv("TZ")?.trim()?.ifBlank { null }?.let { tz ->
		// A leading colon is the POSIX spelling of "what follows names a zone": TZ=":Europe/Paris".
		runCatching { ZoneId.of(tz.removePrefix(":")) }.getOrNull()?.let { return it }
		timeZoneLogger.warn(
			"TZ is set to \"$tz\", which is not an IANA zone ID, so it is being left to the host. " +
				"To name the zone explicitly, use $TIMEZONE_ENV_VAR or the timezone config setting.",
		)
	}
	return ZoneId.systemDefault()
}

private fun parseZone(value: String, source: String): ZoneId =
	try {
		ZoneId.of(value)
	} catch (e: DateTimeException) {
		throw IllegalArgumentException(
			"$source is set to \"$value\", which is not a known time zone. " +
				"Use an IANA zone ID such as \"Europe/Paris\" or \"UTC\".",
			e,
		)
	}

/**
 * Makes [zone] the JVM default, which is what `ZoneId.systemDefault()` (the zone every
 * server-rendered timestamp is formatted in) and Logback's `%d` both read.
 */
fun applyServerTimeZone(zone: ZoneId) {
	if (TimeZone.getDefault().toZoneId() == zone) return
	TimeZone.setDefault(TimeZone.getTimeZone(zone))
	reloadLogbackConfiguration()
}

/**
 * Logback's date converter captures the default zone when it parses `logback.xml`, which happens the
 * first time any logger is created, before the config file has even been read. Reloading rebuilds
 * the converters against the zone just applied, so log lines carry it too.
 */
private fun reloadLogbackConfiguration() {
	val context = LoggerFactory.getILoggerFactory() as? LoggerContext ?: return
	context.reset()
	try {
		ContextInitializer(context).autoConfig()
	} catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
		// reset() already detached every appender, so anything escaping here would leave the server
		// with no console output and an empty admin log viewer.
		System.err.println("Logging config failed to reload after the time zone change: ${e.message}")
		BasicConfigurator().configure(context)
	}
}
