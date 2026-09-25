package com.darkrockstudios.apps.hammer.operations.core

import com.darkrockstudios.apps.hammer.operations.Operation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import org.koin.mp.KoinPlatform
import kotlin.time.Duration.Companion.seconds

/** The operations every build has. Plugin operations are added alongside these. */
fun coreOperations(): List<Operation<*, *>> =
	projectOperations() +
		sceneOperations() +
		contentOperations() +
		searchOperations() +
		statsOperations() +
		accountOperations() +
		syncOperations()

@Serializable
data class ProjectInput(
	/** The project's name, or its server project id. */
	val project: String,
)

@Serializable
data class ProjectItemInput(
	val project: String,
	val id: Int,
)

internal inline fun <reified T : Any> koinGet(): T = KoinPlatform.getKoin().get()

/** A repository flow filled by a background load, which never emits if that load failed. */
internal suspend fun <T> Flow<T>.loaded(what: String): T =
	withTimeoutOrNull(LOAD_TIMEOUT) { first() } ?: error("$what did not load")

private val LOAD_TIMEOUT = 30.seconds
