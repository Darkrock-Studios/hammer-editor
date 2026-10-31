package com.darkrockstudios.apps.hammer.common.data.migrator

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import io.github.aakira.napier.Napier

/**
 * Moves entry images into Image entities (see [EntryImagePromoter]). [DataMigrator] runs the same
 * promotion on every launch, so an image this leaves behind is promoted later.
 */
class Migration4_5(
	private val entryImagePromoter: EntryImagePromoter,
) : Migration {
	override val toVersion: Int = 5

	override fun migrate(projectDef: ProjectDef) {
		Napier.i("Begin Migration4_5 for '${projectDef.name}'...")
		entryImagePromoter.promote(projectDef)
		Napier.i("Migration4_5 for '${projectDef.name}' complete.")
	}
}
