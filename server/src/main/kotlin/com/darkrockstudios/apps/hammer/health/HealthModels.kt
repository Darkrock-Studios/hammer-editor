package com.darkrockstudios.apps.hammer.health

import com.darkrockstudios.apps.hammer.base.http.API_ROUTE_PREFIX
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Root of the health endpoints. Exempt from protocol enforcement, API metrics and call logging. */
const val HEALTH_ROUTE = "/$API_ROUTE_PREFIX/health"

fun isHealthPath(path: String): Boolean = path == HEALTH_ROUTE || path.startsWith("$HEALTH_ROUTE/")

@Serializable
enum class OverallState {
	@SerialName("ok")
	OK,

	@SerialName("failing")
	FAILING,
}

@Serializable
enum class DatabaseState {
	@SerialName("ok")
	OK,

	@SerialName("down")
	DOWN,

	@SerialName("timeout")
	TIMEOUT,
}

@Serializable
enum class JobState {
	@SerialName("ok")
	OK,

	@SerialName("failed")
	FAILED,

	@SerialName("overdue")
	OVERDUE,

	@SerialName("stopped")
	STOPPED,
}

/** What an unauthenticated caller sees: overall state only. */
@Serializable
data class HealthSummary(
	val ok: Boolean,
	val status: OverallState,
)

@Serializable
data class HealthReport(
	val ok: Boolean,
	val status: OverallState,
	val checkedAt: String,
	val database: DatabaseHealth,
	val jobs: Map<String, JobHealth>,
) {
	fun summary() = HealthSummary(ok, status)
}

@Serializable
data class DatabaseHealth(
	val status: DatabaseState,
	val latencyMs: Long?,
) {
	val ok: Boolean get() = status == DatabaseState.OK
}

@Serializable
data class JobHealth(
	val status: JobState,
	val lastRun: String?,
	val nextRun: String?,
	val error: String?,
) {
	val ok: Boolean get() = status == JobState.OK
}
