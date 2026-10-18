package com.darkrockstudios.apps.hammer.utils

import com.darkrockstudios.apps.hammer.e2e.util.SharedPostgresTestDatabase
import com.darkrockstudios.apps.hammer.utilities.getRootDataDirectory
import okio.Path.Companion.toPath
import okio.FileSystem

const val SERVER_EMPTY = "EmptyServer"
const val SERVER_CONFIG_ONE = "ServerConfigOne"

/**
 * Create an in-mem project from a predefined resource
 */
suspend fun createTestServer(
	serverName: String,
	ffs: FileSystem,
	testDatabase: SharedPostgresTestDatabase
) {
	val rootDir = getRootDataDirectory(ffs)
	ffs.createDirectories(rootDir)

	FileResourcesUtils.copyResourceFolderToFakeFileSystem(
		serverName.toPath(),
		rootDir,
		ffs,
		includeFromDir = false
	)

	FileResourcesUtils.setupDatabase(serverName.toPath(), testDatabase)
}
