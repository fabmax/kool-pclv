package de.fabmax.pclv.pointImport

import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.util.BoundingBox
import de.fabmax.kool.util.logI
import de.fabmax.pclv.pointTree.InMemoryOcTree
import de.fabmax.pclv.pointTree.OcTree
import de.fabmax.pclv.pointTree.Point


fun loadPointTree(pointFile: String, bucketSize: Int): OcTree<Point> =
        InMemoryLoader(PlyReader(pointFile), bucketSize).resultTree

private class InMemoryLoader(val reader: PointReader, bucketSize: Int = 5000, val minPointDist: Float = 0.01f) {

    var pointCount = 0
    private val points = HashMap<PointKey, Point>()

    val bounds = BoundingBox()
    val resultTree: OcTree<Point>

    init {
        logI { "Determine point cloud bounds..." }
        bounds.batchUpdate { boundsPass() }
        logI { "$pointCount points, bounds: $bounds" }

        logI { "Build tree structure..." }
        pointsPass()
        resultTree = InMemoryOcTree(points.values, bucketSize)
        logI { "Loaded ${points.size} points" }
    }

    private fun boundsPass() = reader.readPoints(true) {
        bounds.add(it)
        pointCount++
    }

    private fun pointsPass() = reader.readPoints(false) { pt ->
        points[PointKey(pt, bounds.min, minPointDist)] = pt
    }

    private data class PointKey(val x: Int, val y: Int, val z: Int) {
        constructor(pt: Vec3f, min: Vec3f, d: Float) :
                this(((pt.x - min.x) / d).toInt(), ((pt.y - min.y) / d).toInt(), ((pt.z - min.z) / d).toInt())
    }
}
