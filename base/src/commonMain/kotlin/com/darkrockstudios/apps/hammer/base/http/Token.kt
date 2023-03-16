package com.darkrockstudios.apps.hammer.base.http

import kotlinx.serialization.Serializable

@Serializable
data class Token(
    val userId: Long,
    val auth: String,
    val refresh: String
) {
    fun isValid(): Boolean {
        return when {
            auth.length != LENGTH -> false
            !auth.matches(characters) -> false
            else -> true
        }
    }

    companion object {
        private val characters = Regex("[abcdefghijklmnopqrstuvwxyzABCDEFGJKLMNPRSTUVWXYZ0123456789]+")
        const val LENGTH = 64
    }
}