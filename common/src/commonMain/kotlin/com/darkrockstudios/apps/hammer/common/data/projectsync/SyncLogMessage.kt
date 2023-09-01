package com.darkrockstudios.apps.hammer.common.data.projectsync

import com.arkivanov.essenty.parcelable.Parcelable
import com.arkivanov.essenty.parcelable.Parcelize
import com.arkivanov.essenty.parcelable.TypeParceler
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.parcelize.InstantParceler
import io.github.aakira.napier.LogLevel
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

@Parcelize
@TypeParceler<Instant, InstantParceler>()
data class SyncLogMessage(
	val message: String,
	val level: SyncLogLevel,
	val projectName: String?,
	val timestamp: Instant
) : Parcelable

fun syncLogD(message: String, projectName: String) = syncLog(message, projectName, SyncLogLevel.DEBUG)
fun syncLogI(message: String, projectName: String) = syncLog(message, projectName, SyncLogLevel.INFO)
fun syncLogW(message: String, projectName: String) = syncLog(message, projectName, SyncLogLevel.WARN)
fun syncLogE(message: String, projectName: String) = syncLog(message, projectName, SyncLogLevel.ERROR)

fun syncLogD(message: String, project: ProjectDef) = syncLog(message, project.name, SyncLogLevel.DEBUG)
fun syncLogI(message: String, project: ProjectDef) = syncLog(message, project.name, SyncLogLevel.INFO)
fun syncLogW(message: String, project: ProjectDef) = syncLog(message, project.name, SyncLogLevel.WARN)
fun syncLogE(message: String, project: ProjectDef) = syncLog(message, project.name, SyncLogLevel.ERROR)

fun syncAccLogD(message: String) = syncLog(message, null, SyncLogLevel.DEBUG)
fun syncAccLogI(message: String) = syncLog(message, null, SyncLogLevel.INFO)
fun syncAccLogW(message: String) = syncLog(message, null, SyncLogLevel.WARN)
fun syncAccLogE(message: String) = syncLog(message, null, SyncLogLevel.ERROR)


private fun syncLog(message: String, projectName: String?, level: SyncLogLevel): SyncLogMessage {
	return SyncLogMessage(
		message = message,
		level = level,
		projectName = projectName,
		timestamp = Clock.System.now()
	)
}

enum class SyncLogLevel {
	DEBUG, INFO, WARN, ERROR;

	fun toNapierLevel(): LogLevel {
		return when (this) {
			DEBUG -> LogLevel.DEBUG
			INFO -> LogLevel.INFO
			WARN -> LogLevel.WARNING
			ERROR -> LogLevel.ERROR
		}
	}
}

typealias OnSyncLog = suspend (SyncLogMessage) -> Unit