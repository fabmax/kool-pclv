package de.fabmax.pclv.pointTree

import de.fabmax.kool.math.MutableVec3f
import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.util.*
import point_viewer.proto.OctreeMeta
import java.io.File
import java.io.FileInputStream
import java.lang.ref.WeakReference
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min


class OnDiskOcTree(private val dataDirectory: File) : OcTree<Point> {

    override val root: OcTreeNode<Point>

    private val nodes = HashMap<NodeIndex, OnDiskNode>()

    init {
        FileInputStream(File(dataDirectory, "meta.pb")).use {
            val meta = OctreeMeta.Meta.parseFrom(it)
            val bounds = BoundingBox(meta.boundingBox.min.toVec3f(), meta.boundingBox.max.toVec3f())
            meta.nodesList.forEach {
                val node = OnDiskNode(it, bounds)
                nodes[node.nodeIndex] = node
            }

            val numPoints = nodes.values.sumBy { it.numPoints.toInt() }
            logI { "Found Cartographer octree with $numPoints points in ${nodes.size} nodes, bounds: $bounds, version: ${meta.version}" }
        }
        root = nodes[NodeIndex(0, 0L)]!!.apply { assignChildren() }
    }

    private fun OctreeMeta.Vector3f.toVec3f(): Vec3f = Vec3f(x, y, z)

    private inner class OnDiskNode(nodeInfo: OctreeMeta.Node, globalBounds: BoundingBox) : OcTreeNode<Point>() {
        override val nodeName = "${nodeInfo.id.level}-${nodeInfo.id.index}"
        override val bounds = BoundingBox()
        override val numPoints: Long = nodeInfo.numPoints
        override var isLeaf = true
            private set
        override val depth = nodeInfo.id.level

        val children = Array<OnDiskNode?>(8) { null }
        val nodeIndex = NodeIndex(nodeInfo.id)
        val encoding: OctreeMeta.Node.PositionEncoding = nodeInfo.positionEncoding

        private var subTreeWeak = WeakReference<InMemoryOcTree<Point>?>(null)
        val subTree: InMemoryOcTree<Point>
            get() {
                return subTreeWeak.get() ?: InMemoryOcTree(loadPoints(), OcTree.NODE_SIZE, depth, "$nodeName-").also { subTreeWeak = WeakReference(it) }
            }

        init {
            var sizeFac = 1.0
            val min = MutableVec3f(globalBounds.min)
            val edgeLen = max(globalBounds.size.x, max(globalBounds.size.y, globalBounds.size.z))
            for (level in (nodeIndex.level - 1) downTo 0) {
                val idx = (nodeIndex.index shr (3 * level)) and 7
                sizeFac /= 2.0
                min.x += ((idx shr 2) and 1) * sizeFac.toFloat() * edgeLen
                min.y += ((idx shr 1) and 1) * sizeFac.toFloat() * edgeLen
                min.z += ((idx shr 0) and 1) * sizeFac.toFloat() * edgeLen
            }
            val max = MutableVec3f(edgeLen, edgeLen, edgeLen).scale(sizeFac.toFloat()).add(min)
            bounds.set(min, max)
        }

        fun assignChildren() {
            isLeaf = true
            for (i in 0..7) {
                val childId = (nodeIndex.index shl 3) or i.toLong()
                val child = nodes[NodeIndex(nodeIndex.level + 1, childId)]
                if (child != null) {
                    children[i] = child
                    isLeaf = false
                    child.assignChildren()
                }
            }
        }

        override fun collectNodesInFrustum(cam: CameraQuery, result: MutableList<OcTreeNode<Point>>) {
            if (shouldIncludeNode(cam)) {
                subTree.root.collectNodesInFrustum(cam, result)

                children.forEach { it?.collectNodesInFrustum(cam, result) }
            }
        }

        override operator fun get(index: Int): OcTreeNode<Point>? = children[index]

        override fun loadPoints(): List<Point> {
            val points = mutableListOf<Point>()
            val fname = StringBuilder("r")
            for (level in (nodeIndex.level - 1) downTo 0) {
                fname.append((nodeIndex.index shr (level * 3)) and 7)
            }
            val xyzData = File(dataDirectory, fname.toString() + ".xyz")
            val rgbData = File(dataDirectory, fname.toString() + ".rgb")
            val buf = ByteArray(xyzData.length().toInt())
            val wrapped = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN)

            logD { "Loading node data $fname" }

            FileInputStream(xyzData).use {
                var len = buf.size
                while (len > 0) {
                    len -= it.read(buf, buf.size - len, len)
                }

                when (encoding) {
                    OctreeMeta.Node.PositionEncoding.Float32 -> {
                        while (wrapped.remaining() >= 12) {
                            points += decodeFloat32(wrapped.float, wrapped.float, wrapped.float, Point())
                        }
                    }
                    OctreeMeta.Node.PositionEncoding.Uint16 -> {
                        while (wrapped.remaining() >= 6) {
                            points += decodeUint16(wrapped.short, wrapped.short, wrapped.short, Point())
                        }
                    }
                    OctreeMeta.Node.PositionEncoding.Uint8 -> {
                        while (wrapped.remaining() >= 3) {
                            points += decodeUint8(wrapped.get(), wrapped.get(), wrapped.get(), Point())
                        }
                    }
                    else -> logE { "Unsupported position encoding: $encoding" }
                }
            }

            wrapped.rewind()
            FileInputStream(rgbData).use {
                // as long as data is not corrupted rgb data is always smaller than xyz data
                val size = min(buf.size, rgbData.length().toInt())
                var len = size
                while (len > 0) {
                    len -= it.read(buf, size - len, len)
                }
                var i = 0
                while (i < points.size && wrapped.remaining() >= 3) {
                    points[i++].apply {
                        color.r = (wrapped.get().toInt() and 0xff) / 255f
                        color.g = (wrapped.get().toInt() and 0xff) / 255f
                        color.b = (wrapped.get().toInt() and 0xff) / 255f

//                        val f = (color.r + color.g + color.b) / 1.5f
//                        color.set(ColorGradient.PLASMA.getColor(f))

                    }
                }
            }
            return points
        }

        private fun decodeFloat32(x32: Float, y32: Float, z32: Float, result: Point): Point {
            result.x = bounds.min.x + x32 * bounds.size.x
            result.y = bounds.min.y + y32 * bounds.size.y
            result.z = bounds.min.z + z32 * bounds.size.z
            return result
        }

        private fun decodeUint16(x16: Short, y16: Short, z16: Short, result: Point): Point {
            result.x = bounds.min.x + (x16.toInt() and 0xffff) * bounds.size.x / 0xffff
            result.y = bounds.min.y + (y16.toInt() and 0xffff) * bounds.size.y / 0xffff
            result.z = bounds.min.z + (z16.toInt() and 0xffff) * bounds.size.z / 0xffff
            return result
        }

        private fun decodeUint8(x8: Byte, y8: Byte, z8: Byte, result: Point): Point {
            result.x = bounds.min.x + (x8.toInt() and 0xff) * bounds.size.x / 0xff
            result.y = bounds.min.y + (y8.toInt() and 0xff) * bounds.size.y / 0xff
            result.z = bounds.min.z + (z8.toInt() and 0xff) * bounds.size.z / 0xff
            return result
        }
    }

    private data class NodeIndex(val level: Int, val index: Long) {
        constructor(id: OctreeMeta.NodeId) : this(id.level, id.index)
    }
}
