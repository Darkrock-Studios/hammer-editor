package com.darkrockstudios.apps.hammer.common.data.migrator

import co.touchlab.kermit.Logger
import com.darkrockstudios.apps.hammer.common.data.ProjectDef

/**
 * Moves entry images into Image entities (see [EntryImagePromoter]). [DataMigrator] runs the same
 * promotion on every launch, so an image this leaves behind is promoted later.
 */
class Migration4_5(
	private val entryImagePromoter: EntryImagePromoter,
) : Migration {
	override val toVersion: Int = 5

	override fun migrate(projectDef: ProjectDef) {
		Logger.i("Begin Migration4_5 for '${projectDef.name}'...")
		entryImagePromoter.promote(projectDef)
		Logger.i("Migration4_5 for '${projectDef.name}' complete.")
	}
}
