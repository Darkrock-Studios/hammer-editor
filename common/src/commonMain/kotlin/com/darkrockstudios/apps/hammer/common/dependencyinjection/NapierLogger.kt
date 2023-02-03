package com.darkrockstudios.apps.hammer.common.dependencyinjection

import io.github.aakira.napier.LogLevel
import io.github.aakira.napier.Napier
import org.koin.core.logger.Level
import org.koin.core.logger.Logger
import org.koin.core.logger.MESSAGE

class NapierLogger : Logger() {
    override fun display(level: Level, msg: MESSAGE) {
        Napier.log(
            priority = level.toNapierPriority(),
            tag = "Koin",
            throwable = null,
            message = msg
        )
    }
}

private fun Level.toNapierPriority() = when (this) {
    Level.DEBUG -> LogLevel.DEBUG
    Level.INFO -> LogLevel.INFO
    Level.WARNING -> LogLevel.WARNING
    Level.ERROR -> LogLevel.ERROR
    Level.NONE -> LogLevel.VERBOSE
}