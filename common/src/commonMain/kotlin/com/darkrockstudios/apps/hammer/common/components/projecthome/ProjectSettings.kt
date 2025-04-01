package com.darkrockstudios.apps.hammer.common.components.projecthome

import com.darkrockstudios.apps.hammer.common.components.spellchecksettings.SpellCheckSettings
import com.darkrockstudios.apps.hammer.common.dependencyinjection.HammerComponent

interface ProjectSettings : HammerComponent {
	val spellCheckSettings: SpellCheckSettings
}