package com.darkrockstudios.apps.hammer.utils

import com.darkrockstudios.apps.hammer.e2e.util.SqliteTestDatabase
import com.darkrockstudios.apps.hammer.projects.ProjectsFileSystemDatasource
import com.darkrockstudios.apps.hammer.utilities.getRootDataDirectory
import okio.Path
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem

const val SERVER_EMPTY_NO_WHITELIST = "EmptyServerNoWhitelist"
const val SERVER_EMPTY_YES_WHITELIST = "EmptyServerYesWhitelist"
const val SERVER_CONFIG_ONE = "ServerConfigOne"

fun getUserDataDirectory(ffs: FakeFileSystem): Path {
	val rootDir = ProjectsFileSystemDatasource.getRootDirectory(ffs)
	return rootDir
}

/**
 * Create an in-mem project from a predefined resource
 */
suspend fun createTestServer(
	serverName: String,
	ffs: FakeFileSystem,
	testDatabase: SqliteTestDatabase
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
