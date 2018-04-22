package de.fabmax.pclv.pointTree

import de.fabmax.kool.math.MutableVec3f
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.MutableColor

class Point() : MutableVec3f() {

    constructor(x: Float, y: Float, z: Float) : this() {
        set(x, y, z)
    }

    val color = MutableColor(Color.MD_GREY)
}
