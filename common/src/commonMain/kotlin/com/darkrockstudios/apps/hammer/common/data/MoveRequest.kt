package com.darkrockstudios.apps.hammer.common.data

import com.darkrockstudios.apps.hammer.common.data.tree.NodeCoordinates

data class MoveRequest(val id: Int, val toPosition: InsertPosition)

data class InsertPosition(val coords: NodeCoordinates, val before: Boolean = true)