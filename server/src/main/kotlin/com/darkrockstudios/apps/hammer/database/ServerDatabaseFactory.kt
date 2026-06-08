package com.darkrockstudios.apps.hammer.database

import app.cash.sqldelight.db.SqlDriver
import com.darkrockstudios.apps.hammer.Account
import com.darkrockstudios.apps.hammer.Api_metric_bucket
import com.darkrockstudios.apps.hammer.Auth_token
import com.darkrockstudios.apps.hammer.Deleted_entity
import com.darkrockstudios.apps.hammer.Deleted_project
import com.darkrockstudios.apps.hammer.Error_log
import com.darkrockstudios.apps.hammer.Login_attempt
import com.darkrockstudios.apps.hammer.Password_reset_token
import com.darkrockstudios.apps.hammer.Project
import com.darkrockstudios.apps.hammer.Project_access
import com.darkrockstudios.apps.hammer.Project_data
import com.darkrockstudios.apps.hammer.White_list

/**
 * Builds a [ServerDatabase] from a [SqlDriver] with the standard set of column
 * adapters wired in (currently: Instant ↔ OffsetDateTime for every TIMESTAMPTZ
 * column). Centralized so the embedded and remote backends share one source of
 * truth for adapter registration.
 */
fun buildServerDatabase(driver: SqlDriver): ServerDatabase {
	val instant = InstantColumnAdapter
	return ServerDatabase(
		driver = driver,
		accountAdapter = Account.Adapter(
			createdAdapter = instant,
			last_syncAdapter = instant,
		),
		auth_tokenAdapter = Auth_token.Adapter(
			createdAdapter = instant,
			expiresAdapter = instant,
		),
		deleted_entityAdapter = Deleted_entity.Adapter(
			deleted_atAdapter = instant,
		),
		deleted_projectAdapter = Deleted_project.Adapter(
			deleted_atAdapter = instant,
		),
		password_reset_tokenAdapter = Password_reset_token.Adapter(
			createdAdapter = instant,
			expiresAdapter = instant,
		),
		projectAdapter = Project.Adapter(
			last_syncAdapter = instant,
		),
		project_accessAdapter = Project_access.Adapter(
			expires_atAdapter = instant,
			published_atAdapter = instant,
		),
		project_dataAdapter = Project_data.Adapter(
			updated_atAdapter = instant,
		),
		white_listAdapter = White_list.Adapter(
			date_addedAdapter = instant,
		),
		api_metric_bucketAdapter = Api_metric_bucket.Adapter(
			bucket_startAdapter = instant,
		),
		error_logAdapter = Error_log.Adapter(
			first_seenAdapter = instant,
			last_seenAdapter = instant,
			notified_atAdapter = instant,
		),
		login_attemptAdapter = Login_attempt.Adapter(
			attempted_atAdapter = instant,
		),
	)
}
