package com.darkrockstudios.apps.hammer.common.fileio.okio

import com.darkrockstudios.apps.hammer.common.fileio.HPath
import okio.Path
import okio.Path.Companion.toPath

fun Path.toHPath() = HPath(path = toString(), isAbsolute = isAbsolute)
fun HPath.toOkioPath() = path.toPath()