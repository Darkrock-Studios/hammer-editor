package com.darkrockstudios.apps.hammer.common.data

data class SceneContent(val sceneDef: SceneDef, val text: String) {
    fun isContentDifferent(that: SceneContent?) = text != that?.text
}