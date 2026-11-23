package com.darkrockstudios.apps.hammer.common.data.nux

/** One visit to a screen, from [NuxRepository.startVisit]. Keep it across configuration changes so the roll holds. */
class NuxVisit internal constructor(internal val laterVisitOffers: Boolean)
