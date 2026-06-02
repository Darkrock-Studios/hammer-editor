package com.darkrockstudios.apps.hammer.frontend

import com.darkrockstudios.apps.hammer.Error_log
import com.darkrockstudios.apps.hammer.admin.AdminServerConfig
import com.darkrockstudios.apps.hammer.admin.ConfigRepository
import com.darkrockstudios.apps.hammer.frontend.utils.formatInstant
import com.darkrockstudios.apps.hammer.monitoring.EndpointStat
import com.darkrockstudios.apps.hammer.monitoring.ErrorRepository
import com.darkrockstudios.apps.hammer.monitoring.LATENCY_OVERFLOW_MS
import com.darkrockstudios.apps.hammer.monitoring.MetricsRepository
import com.darkrockstudios.apps.hammer.monitoring.SecurityRepository
import com.darkrockstudios.apps.hammer.project.ProjectSynchronizationSession
import com.darkrockstudios.apps.hammer.projects.ProjectsSynchronizationSession
import com.darkrockstudios.apps.hammer.syncsessionmanager.SyncSessionManager
import io.ktor.server.application.*
import io.ktor.server.mustache.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.ceil
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

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
			val alerts = deriveAlerts(stats)
			val chart = buildHourlyChart(metricsRepository.getHourBucketsSince(since))

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
			)

			call.respond(MustacheContent("admin-monitoring.mustache", call.withDefaults(model)))
		}

		get("/performance") {
			val range = call.request.queryParameters["range"] ?: RANGE_24H
			val since = clock.now() - rangeToDuration(range)
			val stats = metricsRepository.getEndpointStats(since)

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
			)

			call.respond(MustacheContent("admin-monitoring-performance.mustache", call.withDefaults(model)))
		}

		get("/errors") {
			val pageSize = 20
			val totalCount = errorRepository.getCount()
			val totalPages = ceil(totalCount.toDouble() / pageSize).toInt()
			val requestedPage = call.request.queryParameters["page"]?.toIntOrNull() ?: 0
			val currentPage = if (totalPages > 0) requestedPage.coerceIn(0, totalPages - 1) else 0
			val errors = errorRepository.getRecent(currentPage, pageSize).map(::errorRowModel)

			val model = mutableMapOf<String, Any>(
				"page_stylesheet" to "/assets/css/admin.css",
				"activeMonitoring" to true,
				"activeMonErrors" to true,
				"patreonFeatureEnabled" to patreonFeatureEnabled,
				"emailFeatureEnabled" to emailFeatureEnabled,
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
	}
}

private fun errorRowModel(e: Error_log): Map<String, Any> = mapOf(
	"type" to e.exception_type,
	"route" to (e.route ?: "—"),
	"user" to (e.user_id?.toString() ?: "all"),
	"hasUser" to (e.user_id != null),
	"count" to e.occurrence_count,
	"lastSeen" to formatInstant(e.last_seen, "MMM dd, HH:mm"),
	"message" to (e.message ?: ""),
	"hasMessage" to (e.message != null),
	"stackTrace" to (e.stack_trace ?: ""),
	"hasStack" to (e.stack_trace != null),
)

// --- model helpers ---

private fun endpointRowModel(s: EndpointStat): Map<String, Any> = mapOf(
	"route" to s.route,
	"method" to s.method,
	"requestCount" to formatCount(s.requestCount),
	"errorCount" to s.errorCount,
	"errorRate" to formatPercent(if (s.requestCount > 0) s.errorCount.toDouble() / s.requestCount else 0.0),
	"hasErrors" to (s.errorCount > 0),
	"p50" to formatLatency(s.p50),
	"p95" to formatLatency(s.p95),
	"p99" to formatLatency(s.p99),
	"avg" to formatLatency(s.avgMs),
)

private fun deriveAlerts(stats: List<EndpointStat>): List<Map<String, Any>> =
	stats.filter { it.requestCount >= ALERT_MIN_REQUESTS && it.errorCount.toDouble() / it.requestCount > ALERT_ERROR_RATE }
		.sortedByDescending { it.errorCount.toDouble() / it.requestCount }
		.map { s ->
			val rate = formatPercent(s.errorCount.toDouble() / s.requestCount)
			mapOf(
				"severity" to "warning",
				"route" to s.route,
				"detail" to "$rate of ${s.requestCount} requests failed in the last 24h",
			)
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

private const val RANGE_24H = "24h"
private const val RANGE_7D = "7d"
private const val RANGE_30D = "30d"
private const val ALERT_MIN_REQUESTS = 20
private const val ALERT_ERROR_RATE = 0.25
