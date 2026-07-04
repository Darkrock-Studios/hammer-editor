package com.darkrockstudios.apps.hammer.base.http.synchronizer

import com.darkrockstudios.apps.hammer.base.http.storyideas.IdeaConflictDto

/** The server rejected a story-idea upload because the client's baseline hash was stale (HTTP 409). */
class IdeaConflictException(val conflict: IdeaConflictDto) : Exception("Story idea conflict")
