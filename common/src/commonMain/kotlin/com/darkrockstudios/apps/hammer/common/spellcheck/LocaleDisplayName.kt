package com.darkrockstudios.apps.hammer.common.spellcheck

import com.darkrockstudios.apps.hammer.common.util.Locale

/**
 * Human-readable name for a locale, localized to the user's current system
 * locale (e.g. "English (United States)"). Falls back to the BCP-47 tag.
 */
expect fun Locale.displayName(): String
