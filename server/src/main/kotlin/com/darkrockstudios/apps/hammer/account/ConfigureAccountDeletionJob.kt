package com.darkrockstudios.apps.hammer.account

import com.darkrockstudios.apps.hammer.scheduling.launchRecurringTask
import io.ktor.server.application.*
import org.koin.ktor.ext.inject

/**
 * Starts the job that permanently deletes accounts past their soft-delete
 * retention window. Always runs; a pass with nothing due is a no-op.
 */
fun Application.configureAccountDeletionJob() {
	val job: AccountDeletionJob by inject()
	launchRecurringTask(job)
	log.info("Account deletion job started")
}
