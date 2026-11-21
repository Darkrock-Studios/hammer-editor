package com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository

import com.darkrockstudios.apps.hammer.common.data.SceneBuffer

/**
 * A scene buffer with its revision, read together. Revisions grow each time a buffer's content is
 * replaced, so they order texts captured at different times.
 */
data class BufferSnapshot(val buffer: SceneBuffer, val revision: Long)
