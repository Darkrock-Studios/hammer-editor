package com.darkrockstudios.apps.hammer.common.data.imagesrepository.image

import com.darkrockstudios.apps.hammer.common.fileio.HPath

/**
 * Where an image is on disk. [contentHash] changes whenever the bytes do, unlike the path, so it
 * keys image caches: the content hash when one is already known, otherwise the file's stamp, which
 * never reads the bytes. Null when the file cannot be read.
 */
data class ImageFile(val def: ImageDef, val path: HPath, val contentHash: String?)
