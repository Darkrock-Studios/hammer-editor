package com.darkrockstudios.apps.hammer.common.util

import com.darkrockstudios.apps.hammer.base.GITHUB_URL
import io.ktor.http.encodeURLParameter

/** The issue form in `.github/ISSUE_TEMPLATE/`. */
internal const val BUG_REPORT_TEMPLATE = "bug_report.yml"

/** The form field GitHub fills from the query parameter of the same name. */
internal const val BUG_REPORT_ENVIRONMENT_FIELD = "environment"

/**
 * A GitHub "new issue" link that opens the bug report form with [environment] already filled in.
 * GitHub only serves issue forms from the repository's default branch.
 */
fun buildBugReportUrl(environment: String, repoUrl: String = GITHUB_URL): String =
	"${repoUrl.trimEnd('/')}/issues/new" +
		"?template=$BUG_REPORT_TEMPLATE" +
		"&$BUG_REPORT_ENVIRONMENT_FIELD=${environment.encodeURLParameter()}"
