package com.darkrockstudios.apps.hammer.common.preview

import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettings
import com.darkrockstudios.apps.hammer.common.data.globalsettings.SpellCheckerSettings
import com.darkrockstudios.apps.hammer.common.spellcheck.Language

val globalSettingsPreview = GlobalSettings(
	projectsDirectory = "",
	spellCheckSettings = SpellCheckerSettings(language = Language.English)
)