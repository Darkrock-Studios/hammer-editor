package com.darkrockstudios.apps.hammer.common.components.actions

/** [modIsCmd]: Mod is Cmd here, and a held Ctrl means no chord. Elsewhere Mod is Ctrl and a held Meta means no chord. */
enum class ChordPlatform(val modIsCmd: Boolean, val showsChordsByDefault: Boolean) {
	DesktopMac(modIsCmd = true, showsChordsByDefault = true),
	Desktop(modIsCmd = false, showsChordsByDefault = true),
	Ios(modIsCmd = true, showsChordsByDefault = false),
	Android(modIsCmd = false, showsChordsByDefault = false),
}

expect val currentChordPlatform: ChordPlatform
