package de.fabmax.pclv.pointTree

import de.fabmax.kool.math.MutableVec2f
import de.fabmax.kool.math.MutableVec3f
import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.util.BoundingBox
import de.fabmax.kool.util.logD

interface OcTree<T: Vec3f> {
    val root: OcTreeNode<T>

    fun collectNodesInFrustum(cam: CameraQuery, maxNodes: Int, maxPoints: Int): MutableList<OcTreeNode<T>> {
        val nodes = mutableListOf<OcTreeNode<T>>()
        root.collectNodesInFrustum(cam, nodes)

        logD { "Retrieved ${nodes.size} nodes containing ${nodes.sumBy { it.numPoints.toInt() }} points" }

        nodes.sortWith(Comparator { n1, n2 ->
            val result = if (n1.depth != n2.depth) {
                compareValues(n1.depth, n2.depth)
            } else {
                compareValues(n1.bounds.center.sqrDistance(cam.globalPos), n2.bounds.center.sqrDistance(cam.globalPos))
            }
            result
        })

        // limit number of nodes
        if (maxNodes > 0 && nodes.size > maxNodes) {
            for (i in nodes.size-1 downTo maxNodes) {
                nodes.removeAt(i)
            }
        }

        // limit number of points
        var numPoints = nodes.sumBy { it.numPoints.toInt() }
        if (maxPoints > 0) {
            while (numPoints > maxPoints && nodes.size > 0) {
                val rem = nodes.removeAt(nodes.lastIndex)
                numPoints -= rem.numPoints.toInt()
            }
        }
        return nodes
    }

    companion object {
        const val NODE_SIZE = 5000
    }
}

abstract class OcTreeNode<T: Vec3f> {
    abstract val nodeName: String
    abstract val numPoints: Long
    abstract val bounds: BoundingBox
    abstract val isLeaf: Boolean
    abstract val depth: Int

    private val projMin = MutableVec2f()
    private val projMax = MutableVec2f()
    private val projSize = MutableVec2f()
    private val tmpV31 = MutableVec3f()
    private val tmpV32 = MutableVec3f()

    abstract fun loadPoints(): List<T>

    abstract operator fun get(index: Int): OcTreeNode<T>?

    open fun childIndexForPoint(point: Vec3f): Int {
        return if (point.x < bounds.center.x) { 0 } else { 4 } or
                if (point.y < bounds.center.y) { 0 } else { 2 } or
                if (point.z < bounds.center.z) { 0 } else { 1 }
    }

    open fun collectNodesInFrustum(cam: CameraQuery, result: MutableList<OcTreeNode<T>>) {
        if (shouldIncludeNode(cam)) {
            result += this
            if (!isLeaf) {
                for (i in 0..7) {
                    this[i]?.collectNodesInFrustum(cam, result)
                }
            }
        }
    }

    open fun isInFrustum(cam: CameraQuery): Boolean {
        val radius = bounds.size.length() / 2
        return cam.isInFrustum(bounds.center, radius)
    }

    open fun shouldIncludeNode(cam: CameraQuery): Boolean =
            isInFrustum(cam) && numPoints > 0 && numPoints / projSize(cam) < cam.maxDensity

    open fun projSize(cam: CameraQuery): Float {
        projMin.set(Float.MAX_VALUE, Float.MAX_VALUE)
        projMax.set(-Float.MAX_VALUE, -Float.MAX_VALUE)
        cam.projectMinMax(tmpV31.set(bounds.min.x, bounds.min.y, bounds.min.z), tmpV32)
        cam.projectMinMax(tmpV31.set(bounds.min.x, bounds.min.y, bounds.max.z), tmpV32)
        cam.projectMinMax(tmpV31.set(bounds.min.x, bounds.max.y, bounds.min.z), tmpV32)
        cam.projectMinMax(tmpV31.set(bounds.min.x, bounds.max.y, bounds.max.z), tmpV32)
        cam.projectMinMax(tmpV31.set(bounds.max.x, bounds.min.y, bounds.min.z), tmpV32)
        cam.projectMinMax(tmpV31.set(bounds.max.x, bounds.min.y, bounds.max.z), tmpV32)
        cam.projectMinMax(tmpV31.set(bounds.max.x, bounds.max.y, bounds.min.z), tmpV32)
        cam.projectMinMax(tmpV31.set(bounds.max.x, bounds.max.y, bounds.max.z), tmpV32)
        projSize.set(projMax).subtract(projMin)
        return projSize.x * projSize.y
    }

    private fun CameraQuery.projectMinMax(pos: Vec3f, result: MutableVec3f) {
        if (project(pos, result)) {
            result.x = (1 + result.x) * 0.5f * viewportWidth
            result.y = (1 + result.y) * 0.5f * viewportHeight
            projMin.setMin(result)
            projMax.setMax(result)
        }
    }

    private fun MutableVec2f.setMin(vec3: Vec3f) {
        if (vec3.x < x) {
            x = vec3.x
        }
        if (vec3.y < y) {
            y = vec3.y
        }
    }

    private fun MutableVec2f.setMax(vec3: Vec3f) {
        if (vec3.x > x) {
            x = vec3.x
        }
        if (vec3.y > y) {
            y = vec3.y
        }
    }
}