package de.fabmax.pclv.pointImport

import de.fabmax.pclv.pointTree.Point

interface PointReader {

    fun readPoints(recyclePoint: Boolean, receiver: (Point) -> Unit)

}