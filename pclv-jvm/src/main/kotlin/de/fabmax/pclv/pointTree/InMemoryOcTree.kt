package de.fabmax.pclv.pointTree

import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.util.BoundingBox
import kotlin.math.max
import kotlin.math.min

class InMemoryOcTree<T: Vec3f>(points: Collection<T>, nodeSize: Int = OcTree.NODE_SIZE, startDepth: Int = 0, val namePrefix: String = "") : OcTree<T> {

    override val root: Node<T>

    private var nextNodeId = 1L

    init {
        val bounds = BoundingBox()
        points.forEach { pt -> bounds.add(pt) }

        // cubify bounds
        val edLen = max(bounds.size.x, max(bounds.size.y, bounds.size.z))
        bounds.set(bounds.min.x, bounds.min.y, bounds.min.z, bounds.min.x + edLen, bounds.min.y + edLen, bounds.min.z + edLen)

        root = Node(bounds, nodeSize, startDepth)
        points.forEach { root.addPoint(it) }

        subSampleNodes()
    }

    fun subSampleNodes() {
        root.randomizePoints()
        root.subSampleChildren()
    }

    fun findNearest(point: Vec3f): T? = root.findNearest(point)

    inner class Node<T: Vec3f>(override val bounds: BoundingBox, nodeSize: Int, override val depth: Int) : OcTreeNode<T>() {
        override val nodeName = "$namePrefix${nextNodeId++}"
        override val numPoints: Long
            get() = points.size.toLong()
        override var isLeaf: Boolean = true

        var nodeIsze = nodeSize
            set(value) {
                field = value
                children.forEach { it?.nodeIsze = value }
                checkMergeJoin()
            }

        private val points = mutableListOf<T>()
        private val children = Array<Node<T>?>(8) { null }

        override fun loadPoints(): List<T> = points

        override operator fun get(index: Int): Node<T>? = children[index]

        fun findNearest(pt: Vec3f, limitD: Float = Float.MAX_VALUE): T? {
            if (isLeaf) {
                return points.minBy { it.sqrDistance(pt) }

            } else {
                val idx = childIndexForPoint(pt)
                val bestChild = children[idx]!!
                var nearest = bestChild.findNearest(pt, limitD)
                var d = min(limitD, nearest?.sqrDistance(pt) ?: Float.MAX_VALUE)

                for (i in children.indices) {
                    if (children[i] == bestChild) {
                        continue
                    }
                    val bd = children[i]!!.bounds.pointDistanceSqr(pt)
                    if (bd < d) {
                        val n = children[i]!!.findNearest(pt, d)
                        val nd = n?.sqrDistance(pt) ?: Float.MAX_VALUE
                        if (nd < d ) {
                            d = nd
                            nearest = n
                        }
                    }
                }
                return nearest
            }
        }

        fun addPoint(point: T) {
//            if (!bounds.isIncluding(point)) {
//                throw Exception("Point is not in bounds")
//            }
            if (isLeaf) {
                points += point
                if (points.size > nodeIsze) {
                    split()
                }
            } else {
                addInChild(point)
            }
        }

        internal fun randomizePoints() {
            points.shuffle()
            children.forEach { it?.randomizePoints() }
        }

        internal fun subSampleChildren() {
            if (!isLeaf) {
                points.clear()

                for (i in children.indices) {
                    val c = children[i] ?: continue
                    c.subSampleChildren()
                    if (c.numPoints == 0L) {
                        children[i] = null
                    }
                }
                children.forEach { it?.subSampleChildren() }

                var cIdx = 0
                var added = true
                while (added && points.size < nodeIsze) {
                    added = false
                    for (i in children.indices) {
                        val c = children[i] ?: continue
                        if (cIdx < c.points.size) {
                            points += c.points[cIdx]
                            added = true
                        }
                    }
                    cIdx++
                }
            }
        }

        private fun addInChild(point: T) {
            val idx = childIndexForPoint(point)
            children[idx]!!.addPoint(point)
        }

        private fun checkMergeJoin() {
            if (isLeaf && points.size > nodeIsze) {
                split()
            } else if (!isLeaf) {
                var sumCnt = 0
                for (i in children.indices) {
                    if (!children[i]!!.isLeaf) {
                        return
                    } else {
                        sumCnt += children[i]!!.points.size
                    }
                }
                if (sumCnt <= nodeIsze) {
                    isLeaf = true
                    points.clear()
                    for (i in children.indices) {
                        points += children[i]!!.points
                        children[i] = null
                    }
                }
            }
        }

        private fun split() {
            isLeaf = false

            val x0 = bounds.min.x
            val x1 = bounds.center.x
            val x2 = bounds.max.x

            val y0 = bounds.min.y
            val y1 = bounds.center.y
            val y2 = bounds.max.y

            val z0 = bounds.min.z
            val z1 = bounds.center.z
            val z2 = bounds.max.z

            children[0] = Node(BoundingBox(Vec3f(x0, y0, z0), Vec3f(x1, y1, z1)), nodeIsze, depth + 1)
            children[1] = Node(BoundingBox(Vec3f(x0, y0, z1), Vec3f(x1, y1, z2)), nodeIsze, depth + 1)
            children[2] = Node(BoundingBox(Vec3f(x0, y1, z0), Vec3f(x1, y2, z1)), nodeIsze, depth + 1)
            children[3] = Node(BoundingBox(Vec3f(x0, y1, z1), Vec3f(x1, y2, z2)), nodeIsze, depth + 1)

            children[4] = Node(BoundingBox(Vec3f(x1, y0, z0), Vec3f(x2, y1, z1)), nodeIsze, depth + 1)
            children[5] = Node(BoundingBox(Vec3f(x1, y0, z1), Vec3f(x2, y1, z2)), nodeIsze, depth + 1)
            children[6] = Node(BoundingBox(Vec3f(x1, y1, z0), Vec3f(x2, y2, z1)), nodeIsze, depth + 1)
            children[7] = Node(BoundingBox(Vec3f(x1, y1, z1), Vec3f(x2, y2, z2)), nodeIsze, depth + 1)

            points.forEach(this::addInChild)
            points.clear()
        }
    }
}