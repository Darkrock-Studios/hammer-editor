package com.darkrockstudios.apps.hammer.common.data.nux

/** The platforms a [NuxHint] can apply to. */
enum class NuxPlatform { Android, Desktop, Ios }

expect val currentNuxPlatform: NuxPlatform
