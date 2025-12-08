package com.darkrockstudios.apps.hammer.common.preview

import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettings
import com.darkrockstudios.apps.hammer.common.data.globalsettings.SpellCheckerSettings
import io.fluidsonic.locale.Locale

val globalSettingsPreview = GlobalSettings(
	projectsDirectory = "",
	spellCheckSettings = SpellCheckerSettings(locale = Locale.root)
)