package com.darkrockstudios.apps.hammer.common.data.sync.accountsync

import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository

/** Which side's value an attribute takes this session. */
enum class Winner { Agree, TakeServer, PushLocal }

/**
 * Reconciles one attribute against the last value both sides agreed on. Only a side that alone moved
 * off the baseline is taken; with no baseline, or when both moved, the server wins.
 */
fun threeWay(
	localEqualsServer: Boolean,
	localEqualsBaseline: Boolean,
	serverEqualsBaseline: Boolean,
): Winner = when {
	localEqualsServer -> Winner.Agree
	serverEqualsBaseline && !localEqualsBaseline -> Winner.PushLocal
	else -> Winner.TakeServer
}

/** Without a baseline neither side equals it, so the server wins. */
fun <T> threeWay(local: T, server: T, baseline: T, hasBaseline: Boolean): Winner =
	threeWay(local == server, hasBaseline && local == baseline, hasBaseline && server == baseline)

/**
 * Names are compared as this device stores them: a server name it cannot store arrives sanitized
 * (see [ProjectsRepository.toLocalSafeName]), and a baseline holds the raw server name.
 */
fun nameThreeWay(local: String, server: String, baseline: String?): Winner =
	threeWay(
		localEqualsServer = local == ProjectsRepository.toLocalSafeName(server),
		localEqualsBaseline = baseline != null && local == ProjectsRepository.toLocalSafeName(baseline),
		serverEqualsBaseline = server == baseline,
	)
