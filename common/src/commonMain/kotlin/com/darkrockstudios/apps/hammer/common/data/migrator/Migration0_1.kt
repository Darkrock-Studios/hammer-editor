package com.darkrockstudios.apps.hammer.common.data.migrator

import com.darkrockstudios.apps.hammer.base.http.writeToml
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineContainer
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineRepositoryOkio
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import io.github.aakira.napier.Napier
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import net.peanuuutz.tomlkt.Toml
import okio.FileNotFoundException
import okio.FileSystem

class Migration0_1(
	private val fileSystem: FileSystem,
	private val toml: Toml,
	private val json: Json,
) : Migration {
	override val toVersion: Int = 1
	override fun migrate(projectDef: ProjectDef) {
		Napier.i("Begin Migration0_1 for '${projectDef.name}'...")

		val path = TimeLineRepositoryOkio.getTimelineFile(projectDef)
		try {
			val timeline = loadJsonTimeline(
				hpath = path,
				fileSystem = fileSystem,
				json = json
			)

			if (timeline != null) {
				fileSystem.writeToml<TimeLineContainer>(path.toOkioPath(), toml, timeline)
			} else {
				Napier.i("No timeline content found for Migration0_1, skipping.")
			}
		} catch (e: FileNotFoundException) {
			Napier.i("No timeline found for Migration0_1, skipping.")
		} catch (e: SerializationException) {
			Napier.w("Failed to deserialize Timeline, it's likely it is already migrated")
		} catch (e: IllegalArgumentException) {
			Napier.w("Failed to deserialize Timeline, it's likely it is already migrated")
		}
	}

	private fun loadJsonTimeline(hpath: HPath, fileSystem: FileSystem, json: Json): TimeLineContainer? {
		val path = hpath.toOkioPath()
		return if (fileSystem.exists(path)) {
			fileSystem.read(path) {
				val timelineToml = readUtf8()
				if (timelineToml.isNotBlank()) {
					json.decodeFromString(timelineToml)
				} else {
					TimeLineContainer(emptyList())
				}
			}
		} else {
			null
		}
	}
}