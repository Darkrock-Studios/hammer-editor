package com.darkrockstudios.apps.hammer.storyideas

import com.darkrockstudios.apps.hammer.base.IdeaId

class IdeaNotFound(val ideaId: IdeaId) : Exception("Idea not found: ${ideaId.id}")

/** Upload rejected because the idea has a deletion tombstone — deletion wins over stale copies. */
class IdeaDeletedException(val ideaId: IdeaId) : Exception("Idea has been deleted: ${ideaId.id}")

class IdeaTooLargeException(val size: Int, val max: Int) :
	Exception("Idea payload is $size bytes once encrypted, over the $max cap")
