package com.darkrockstudios.apps.hammer.utils

import com.darkrockstudios.apps.hammer.projects.ProjectsFileSystemDatasource
import com.darkrockstudios.apps.hammer.utilities.getRootDataDirectory
import okio.Path
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem

const val SERVER_EMPTY_NO_WHITELIST = "EmptyServerNoWhitelist"

fun getUserDataDirectory(ffs: FakeFileSystem): Path {
	val rootDir = ProjectsFileSystemDatasource.getRootDirectory(ffs)
	return rootDir
}

/**
 * Create an in-mem project from a predefined resource
 */
fun createTestServer(serverName: String, ffs: FakeFileSystem) {
	val rootDir = getRootDataDirectory(ffs)
	ffs.createDirectories(rootDir)

	FileResourcesUtils.copyResourceFolderToFakeFileSystem(
		serverName.toPath(),
		rootDir,
		ffs,
		false
	)
}
