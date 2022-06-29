package com.darkrockstudios.apps.hammer.common.data

data class SceneContent(
    val sceneDef: SceneDef,
    val markdown: String? = null,
    val platformRepresentation: PlatformRichText? = null
) {
    init {
        validate()
    }

    private fun validate() {
        if (markdown == null && platformRepresentation == null) {
            throw IllegalArgumentException("markdown and platformRepresentation can not both be null")
        } else if (markdown != null && platformRepresentation != null) {
            throw IllegalArgumentException("markdown and platformRepresentation can not both be non-null")
        }
    }

    fun coerceMarkdown(): String {
        return if (markdown != null) {
            markdown
        } else if (platformRepresentation != null) {
            platformRepresentation.convertToMarkdown()
        } else {
            throw IllegalStateException("SceneContent had no content")
        }
    }

    override fun equals(other: Any?): Boolean {
        return if (other is SceneContent) {
            if (markdown != null && other.markdown != null) {
                markdown == other.markdown
            } else if (platformRepresentation != null && other.platformRepresentation != null) {
                platformRepresentation == other.platformRepresentation
            } else if (markdown != null && other.markdown == null) {
                markdown == other.platformRepresentation!!.convertToMarkdown()
            } else if (markdown == null && other.markdown != null) {
                platformRepresentation!!.convertToMarkdown() == other.markdown
            } else {
                false
            }
        } else {
            false
        }
    }
}

interface PlatformRichText {
    fun convertToMarkdown(): String
    fun compare(text: PlatformRichText): Boolean
}