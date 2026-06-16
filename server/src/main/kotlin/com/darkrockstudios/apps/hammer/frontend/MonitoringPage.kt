package com.darkrockstudios.apps.hammer.frontend

import com.darkrockstudios.apps.hammer.Error_log
import com.darkrockstudios.apps.hammer.admin.AdminServerConfig
import com.darkrockstudios.apps.hammer.admin.ConfigRepository
import com.darkrockstudios.apps.hammer.frontend.utils.formatInstant
import com.darkrockstudios.apps.hammer.monitoring.DailyActiveUsers
import com.darkrockstudios.apps.hammer.monitoring.EndpointStat
import com.darkrockstudios.apps.hammer.monitoring.ErrorRepository
import com.darkrockstudios.apps.hammer.monitoring.LATENCY_OVERFLOW_MS
import com.darkrockstudios.apps.hammer.monitoring.LogLine
import com.darkrockstudios.apps.hammer.monitoring.LogRingBuffer
import com.darkrockstudios.apps.hammer.monitoring.MetricsRepository
import com.darkrockstudios.apps.hammer.monitoring.SecurityAlert
import com.darkrockstudios.apps.hammer.monitoring.SecurityAlerts
import com.darkrockstudios.apps.hammer.monitoring.SecurityRepository
import com.darkrockstudios.apps.hammer.monitoring.StoryReaderRepository
import com.darkrockstudios.apps.hammer.monitoring.TimeSeriesPoint
import com.darkrockstudios.apps.hammer.monitoring.UserActivityRepository
import com.darkrockstudios.apps.hammer.project.ProjectSynchronizationSession
import com.darkrockstudios.apps.hammer.projects.ProjectsSynchronizationSession
import com.darkrockstudios.apps.hammer.syncsessionmanager.SyncSessionManager
import io.ktor.server.mustache.MustacheContent
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.net.URLEncoder
import kotlin.math.ceil
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * The Monitoring section of the admin UI: an "analytics ledger" extension of the
 * Writer's Desk system. Overview is the glanceable dashboard; Performance is the
 * per-endpoint deep dive. Registered under the already-admin-gated /admin route.
 *
 * Dependencies are resolved from Koin lazily inside each request handler (rather
 * than injected when routes are built) so that route tests which boot the app
 * with a narrow mock module don't have to define monitoring beans they never use.
 */
fun Route.adminMonitoringPages(
	metricsRepository: MetricsRepository,
	configRepository: ConfigRepository,
	errorRepository: ErrorRepository,
	securityRepository: SecurityRepository,
	userActivityRepository: UserActivityRepository,
	storyReaderRepository: StoryReaderRepository,
	projectsSyncManager: SyncSessionManager<Long, ProjectsSynchronizationSession>,
	projectSyncManager: SyncSessionManager<*, ProjectSynchronizationSession>,
	clock: Clock,
	patreonFeatureEnabled: Boolean,
	emailFeatureEnabled: Boolean,
) {
	route("/monitoring") {
		get {
			val enabled = configRepository.get(AdminServerConfig.MONITORING_CONFIG).enabled
			val since = clock.now() - 24.hours

			val totals = metricsRepository.getTotals(since)
			val stats = metricsRepository.getEndpointStats(since)
			val activeSyncs = projectsSyncManager.activeSessionCount() + projectSyncManager.activeSessionCount()

			val topSlow = stats.sortedByDescending { it.p95 }.take(5).map(::endpointRowModel)
			val securitySince = clock.now() - SecurityAlerts.WINDOW
			val securityAlerts = SecurityAlerts.derive(
				securityRepository.bruteForceEmails(securitySince),
				securityRepository.bruteForceIps(securitySince),
			).map(::securityAlertModel)
			// Critical security alerts lead; endpoint error-rate warnings follow.
			val alerts = securityAlerts + deriveAlerts(stats)
			val chart = buildHourlyChart(metricsRepository.getHourBucketsSince(since))

			val now = clock.now()
			val activeUsers = userActivityRepository.activeUsersOverview(now)
			val hasActiveUsers = with(activeUsers) {
				listOf(sync.h24, sync.d7, sync.d30, web.h24, web.d7, web.d30).any { it > 0 }
			}

			val readers = storyReaderRepository.readerCounts(now)
			val hasReaders = listOf(readers.h24, readers.d7, readers.d30).any { it > 0 }

			val model = mutableMapOf<String, Any>(
				"page_stylesheet" to "/assets/css/admin.css",
				"activeMonitoring" to true,
				"activeMonOverview" to true,
				"patreonFeatureEnabled" to patreonFeatureEnabled,
				"emailFeatureEnabled" to emailFeatureEnabled,
				"monitoringEnabled" to enabled,
				"statRequests" to formatCount(totals.requestCount),
				"statErrorRate" to formatPercent(totals.errorRate),
				"statP95" to formatLatency(totals.p95Ms),
				"statActiveSyncs" to activeSyncs,
				"hasAlerts" to alerts.isNotEmpty(),
				"alerts" to alerts,
				"topSlow" to topSlow,
				"hasTopSlow" to topSlow.isNotEmpty(),
				"chartJson" to chart,
				"hasActiveUsers" to hasActiveUsers,
				"usersSync24h" to formatCount(activeUsers.sync.h24),
				"usersSync7d" to formatCount(activeUsers.sync.d7),
				"usersSync30d" to formatCount(activeUsers.sync.d30),
				"usersWeb24h" to formatCount(activeUsers.web.h24),
				"usersWeb7d" to formatCount(activeUsers.web.d7),
				"usersWeb30d" to formatCount(activeUsers.web.d30),
				"activeUsersChartJson" to buildActiveUsersChart(activeUsers.daily),
				"hasReaders" to hasReaders,
				"readers24h" to formatCount(readers.h24),
				"readers7d" to formatCount(readers.d7),
				"readers30d" to formatCount(readers.d30),
			)

			call.respond(MustacheContent("admin-monitoring.mustache", call.withDefaults(model)))
		}

		get("/performance") {
			val range = call.request.queryParameters["range"] ?: RANGE_24H
			val since = clock.now() - rangeToDuration(range)
			val stats = metricsRepository.getEndpointStats(since)
			val labelFormat = if (range == RANGE_24H) "HH:00" else "MMM dd"
			val timeSeries = metricsRepository.getTimeSeries(since, range == RANGE_24H)
			val latencyChart = buildLatencyChart(timeSeries, labelFormat)

			val model = mutableMapOf<String, Any>(
				"page_stylesheet" to "/assets/css/admin.css",
				"activeMonitoring" to true,
				"activeMonPerformance" to true,
				"patreonFeatureEnabled" to patreonFeatureEnabled,
				"emailFeatureEnabled" to emailFeatureEnabled,
				"range24h" to (range == RANGE_24H),
				"range7d" to (range == RANGE_7D),
				"range30d" to (range == RANGE_30D),
				"endpoints" to stats.map(::endpointRowModel),
				"hasEndpoints" to stats.isNotEmpty(),
				"latencyChartJson" to latencyChart,
			)

			call.respond(MustacheContent("admin-monitoring-performance.mustache", call.withDefaults(model)))
		}

		get("/errors") {
			val range = call.request.queryParameters["range"] ?: RANGE_24H
			val since = clock.now() - rangeToDuration(range)
			val labelFormat = if (range == RANGE_24H) "HH:00" else "MMM dd"
			val timeSeries = metricsRepository.getTimeSeries(since, range == RANGE_24H)
			val errorRateChart = buildErrorRateChart(timeSeries, labelFormat)

			val routeFilter = call.request.queryParameters["route"]?.takeIf { it.isNotBlank() }
			val pageSize = 20
			val totalCount = errorRepository.getCount(routeFilter)
			val totalPages = ceil(totalCount.toDouble() / pageSize).toInt()
			val requestedPage = call.request.queryParameters["page"]?.toIntOrNull() ?: 0
			val currentPage = if (totalPages > 0) requestedPage.coerceIn(0, totalPages - 1) else 0
			val errors = errorRepository.getRecent(currentPage, pageSize, routeFilter).map(::errorRowModel)

			val model = mutableMapOf<String, Any>(
				"page_stylesheet" to "/assets/css/admin.css",
				"activeMonitoring" to true,
				"activeMonErrors" to true,
				"patreonFeatureEnabled" to patreonFeatureEnabled,
				"emailFeatureEnabled" to emailFeatureEnabled,
				"range24h" to (range == RANGE_24H),
				"range7d" to (range == RANGE_7D),
				"range30d" to (range == RANGE_30D),
				"range" to range,
				"hasRouteFilter" to (routeFilter != null),
				"routeFilter" to (routeFilter ?: ""),
				"routeFilterEnc" to (routeFilter?.let { URLEncoder.encode(it, "UTF-8") } ?: ""),
				"errorRateChartJson" to errorRateChart,
				"errors" to errors,
				"hasErrors" to errors.isNotEmpty(),
				"currentPageDisplay" to currentPage + 1,
				"totalPages" to totalPages,
				"hasPrev" to (currentPage > 0),
				"hasNext" to (currentPage < totalPages - 1),
				"prevPage" to currentPage - 1,
				"nextPage" to currentPage + 1,
				"isPaged" to (totalPages > 1),
			)

			call.respond(MustacheContent("admin-monitoring-errors.mustache", call.withDefaults(model)))
		}

		// Full dump of the grouped errors (respecting the route filter) as JSON —
		// a complete, machine-readable format self-hosters can hand to maintainers.
		get("/errors/export") {
			val routeFilter = call.request.queryParameters["route"]?.takeIf { it.isNotBlank() }
			val total = errorRepository.getCount(routeFilter).toInt()
			val export = errorRepository.getRecent(0, total, routeFilter).map { e ->
				ErrorExport(
					exceptionType = e.exception_type,
					route = e.route,
					userId = e.user_id,
					status = e.status,
					occurrences = e.occurrence_count,
					firstSeen = e.first_seen.toString(),
					lastSeen = e.last_seen.toString(),
					message = e.message,
					stackTrace = e.stack_trace,
				)
			}
			val json = errorExportJson.encodeToString(ListSerializer(ErrorExport.serializer()), export)
			call.response.headers.append("Content-Disposition", "attachment; filename=\"hammer-errors.json\"")
			call.respondText(json, io.ktor.http.ContentType.Application.Json)
		}

		get("/security") {
			val since = clock.now() - 24.hours
			val attempts = securityRepository.getRecentAttempts(0, 50).map { a ->
				mapOf(
					"email" to (a.email ?: "—"),
					"ip" to (a.ip_address ?: "—"),
					"success" to a.success,
					"time" to formatInstant(a.attempted_at, "MMM dd, HH:mm:ss"),
				)
			}
			val topFailures = securityRepository.getTopFailingEmails(since, 10).map { f ->
				mapOf("email" to (f.email ?: "—"), "failures" to f.failures)
			}

			val model = mutableMapOf<String, Any>(
				"page_stylesheet" to "/assets/css/admin.css",
				"activeMonitoring" to true,
				"activeMonSecurity" to true,
				"patreonFeatureEnabled" to patreonFeatureEnabled,
				"emailFeatureEnabled" to emailFeatureEnabled,
				"topFailures" to topFailures,
				"hasTopFailures" to topFailures.isNotEmpty(),
				"attempts" to attempts,
				"hasAttempts" to attempts.isNotEmpty(),
			)

			call.respond(MustacheContent("admin-monitoring-security.mustache", call.withDefaults(model)))
		}

		get("/logs") {
			val model = mutableMapOf<String, Any>(
				"page_stylesheet" to "/assets/css/admin.css",
				"activeMonitoring" to true,
				"activeMonLogs" to true,
				"patreonFeatureEnabled" to patreonFeatureEnabled,
				"emailFeatureEnabled" to emailFeatureEnabled,
			)
			call.respond(MustacheContent("admin-monitoring-logs.mustache", call.withDefaults(model)))
		}

		// HTMX-polled fragment: the live tail, filtered by level + search query.
		get("/logs/tail") {
			val level = call.request.queryParameters["level"]
			val query = call.request.queryParameters["q"]
			val lines = LogRingBuffer.recent(minLevel = level, query = query, limit = 250).map(::logLineModel)
			val model = call.withDefaults(
				mutableMapOf<String, Any>("lines" to lines, "hasLines" to lines.isNotEmpty()),
			)
			call.respond(MustacheContent("partials/log-lines.mustache", model))
		}

		get("/logs/export") {
			val level = call.request.queryParameters["level"]
			val query = call.request.queryParameters["q"]
			val lines = LogRingBuffer.recent(minLevel = level, query = query, limit = LogRingBuffer.CAPACITY)
			val text = buildString {
				lines.forEach { line ->
					val ts = formatInstant(Instant.fromEpochMilliseconds(line.timestampMillis), "yyyy-MM-dd HH:mm:ss.SSS")
					appendLine("[$ts] ${line.level.padEnd(5)} ${line.logger} - ${line.message}")
				}
			}
			call.response.headers.append("Content-Disposition", "attachment; filename=\"server-logs.txt\"")
			call.respondText(text, io.ktor.http.ContentType.Text.Plain)
		}
	}
}

private fun logLineModel(line: LogLine): Map<String, Any> = mapOf(
	"time" to formatInstant(Instant.fromEpochMilliseconds(line.timestampMillis), "HH:mm:ss.SSS"),
	"level" to line.level,
	"levelClass" to line.level.lowercase(),
	"logger" to line.logger,
	"message" to line.message,
)

private fun errorRowModel(e: Error_log): Map<String, Any> {
	val severity = severityFor(e.status)
	return mapOf(
		"type" to e.exception_type,
		"route" to (e.route ?: "—"),
		"user" to (e.user_id?.toString() ?: "all"),
		"hasUser" to (e.user_id != null),
		"count" to e.occurrence_count,
		"status" to e.status,
		"severity" to severity,
		"severityIcon" to if (severity == "warning") "fa-triangle-exclamation" else "fa-circle-exclamation",
		"lastSeen" to formatInstant(e.last_seen, "MMM dd, HH:mm"),
		"message" to (e.message ?: ""),
		"hasMessage" to (e.message != null),
		"stackTrace" to (e.stack_trace ?: ""),
		"hasStack" to (e.stack_trace != null),
	)
}

/** Server-fault 5xx errors are loud; client-fault 4xx errors are quieter warnings. */
private fun severityFor(status: Int): String = if (status in 400..499) "warning" else "error"

// --- model helpers ---

private fun endpointRowModel(s: EndpointStat): Map<String, Any> = mapOf(
	"route" to s.route,
	"method" to s.method,
	"requestCount" to formatCount(s.requestCount),
	"requestCountRaw" to s.requestCount,
	"errorCount" to s.errorCount,
	"errorRate" to formatPercent(if (s.requestCount > 0) s.errorCount.toDouble() / s.requestCount else 0.0),
	"errorRateRaw" to if (s.requestCount > 0) (s.errorCount.toDouble() / s.requestCount * 100000).toLong() else 0L,
	"hasErrors" to (s.errorCount > 0),
	"p50" to formatLatency(s.p50),
	"p50Raw" to if (s.p50 == LATENCY_OVERFLOW_MS) 9999L else s.p50,
	"p95" to formatLatency(s.p95),
	"p95Raw" to if (s.p95 == LATENCY_OVERFLOW_MS) 9999L else s.p95,
	"p99" to formatLatency(s.p99),
	"p99Raw" to if (s.p99 == LATENCY_OVERFLOW_MS) 9999L else s.p99,
	"avg" to formatLatency(s.avgMs),
)

internal fun deriveAlerts(stats: List<EndpointStat>): List<Map<String, Any>> =
	stats.filter { it.requestCount >= ALERT_MIN_REQUESTS && it.errorCount.toDouble() / it.requestCount > ALERT_ERROR_RATE }
		.sortedByDescending { it.errorCount.toDouble() / it.requestCount }
		.map { s ->
			val rate = formatPercent(s.errorCount.toDouble() / s.requestCount)
			mapOf(
				"severity" to "warning",
				"route" to s.route,
				"detail" to "$rate of ${s.requestCount} requests failed in the last 24h",
				"href" to "/admin/monitoring/errors?route=${URLEncoder.encode(s.route, "UTF-8")}",
			)
		}

/** Renders a [SecurityAlert] into the Overview alert model (critical; links to the Security panel). */
private fun securityAlertModel(alert: SecurityAlert): Map<String, Any> = mapOf(
	"severity" to "critical",
	"route" to alert.subject,
	"detail" to alert.detail,
	"href" to "/admin/monitoring/security",
)

private fun buildErrorRateChart(points: List<TimeSeriesPoint>, labelFormat: String): String {
	val payload = ErrorRateChartPayload(
		labels = points.map { formatInstant(it.bucketStart, labelFormat) },
		errorRates = points.map { pt ->
			if (pt.requests > 0) pt.errors.toDouble() / pt.requests * 100.0 else 0.0
		},
	)
	return Json.encodeToString(ErrorRateChartPayload.serializer(), payload)
}

private fun buildLatencyChart(points: List<TimeSeriesPoint>, labelFormat: String): String {
	val payload = LatencyChartPayload(
		labels = points.map { formatInstant(it.bucketStart, labelFormat) },
		p95Ms = points.map { it.p95Ms },
	)
	return Json.encodeToString(LatencyChartPayload.serializer(), payload)
}

private fun buildActiveUsersChart(daily: List<DailyActiveUsers>): String {
	val payload = ActiveUsersChartPayload(
		labels = daily.map { formatInstant(it.day, "MMM dd") },
		sync = daily.map { it.sync },
		web = daily.map { it.web },
	)
	return Json.encodeToString(ActiveUsersChartPayload.serializer(), payload)
}

private fun buildHourlyChart(buckets: List<com.darkrockstudios.apps.hammer.Api_metric_bucket>): String {
	val byHour = buckets.groupBy { it.bucket_start }.toSortedMap()
	val payload = ChartPayload(
		labels = byHour.keys.map { formatInstant(it, "HH:00") },
		requests = byHour.values.map { rows -> rows.sumOf { it.request_count } },
		errors = byHour.values.map { rows -> rows.sumOf { it.error_count } },
	)
	return Json.encodeToString(ChartPayload.serializer(), payload)
}

private fun rangeToDuration(range: String) = when (range) {
	RANGE_7D -> 7.days
	RANGE_30D -> 30.days
	else -> 1.days
}

private fun formatCount(n: Long): String = "%,d".format(n)

private fun formatPercent(rate: Double): String = "%.1f%%".format(rate * 100)

private fun formatLatency(ms: Long): String = when {
	ms == LATENCY_OVERFLOW_MS -> ">2.5s"
	ms >= 1000 -> "%.1fs".format(ms / 1000.0)
	else -> "${ms}ms"
}

@Serializable
private data class ChartPayload(
	val labels: List<String>,
	val requests: List<Long>,
	val errors: List<Long>,
)

@Serializable
private data class ActiveUsersChartPayload(
	val labels: List<String>,
	val sync: List<Long>,
	val web: List<Long>,
)

private val errorExportJson = Json { prettyPrint = true }

@Serializable
private data class ErrorExport(
	val exceptionType: String,
	val route: String?,
	val userId: Long?,
	val status: Int,
	val occurrences: Long,
	val firstSeen: String,
	val lastSeen: String,
	val message: String?,
	val stackTrace: String?,
)

@Serializable
private data class ErrorRateChartPayload(
	val labels: List<String>,
	val errorRates: List<Double>,
)

@Serializable
private data class LatencyChartPayload(
	val labels: List<String>,
	val p95Ms: List<Long>,
)

private const val RANGE_24H = "24h"
private const val RANGE_7D = "7d"
private const val RANGE_30D = "30d"
internal const val ALERT_MIN_REQUESTS = 20
internal const val ALERT_ERROR_RATE = 0.25
