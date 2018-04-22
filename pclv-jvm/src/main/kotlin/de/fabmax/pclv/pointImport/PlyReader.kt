package de.fabmax.pclv.pointImport

import de.fabmax.kool.math.*
import de.fabmax.kool.util.BoundingBox
import de.fabmax.kool.util.Log
import de.fabmax.kool.util.logD
import de.fabmax.kool.util.logI
import de.fabmax.kool.util.serialization.MeshConverter
import de.fabmax.kool.util.serialization.MeshData
import de.fabmax.pclv.pointTree.InMemoryOcTree
import de.fabmax.pclv.pointTree.OcTree
import de.fabmax.pclv.pointTree.Point
import java.io.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

class PlyReader(private val pointFile: String) : PointReader {

    private var numVertices = 0
    private var headerSize = 0
    private var vertexSize = 0

    private val propertyParsers = mutableListOf<PropertyParser>()

    init {
        readPlyHeader()
    }

    override fun readPoints(recyclePoint: Boolean, receiver: (Point) -> Unit) {
        FileInputStream(pointFile).use { fis ->
            fis.skip(headerSize.toLong())

            var point = Point()
            val vertBatchSize = 1000
            var cnt = 0
            val buf = ByteArray(vertexSize * vertBatchSize)
            val bbuf = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN)

            var k = 0
            var l = 0
            while (k < numVertices) {
                val readVerts = fis.read(buf) / vertexSize
                k += readVerts
                if (++l % 1000 == 0) {
                    logD { "Read ${k/1_000_000}M points..." }
                }

                for (i in 0 until readVerts) {
                    for (j in propertyParsers.indices) {
                        propertyParsers[j].parser(bbuf, point)
                    }
                    receiver(point)
                    if (!recyclePoint) {
                        point = Point()
                    }
                }
                bbuf.rewind()
            }
        }
    }

    private fun readPlyHeader() {
        FileInputStream(pointFile).use { fis ->
            val magic = "${fis.read().toChar()}${fis.read().toChar()}${fis.read().toChar()}${fis.read().toChar()}"
            if (magic != "ply\n") {
                throw Exception("Invalid input file: $magic")
            }
            headerSize = 4

            var line = fis.readHeaderLine()
            var iElement = 0
            var currentElement = ""
            while (line != "end_header") {
                headerSize += line.length + 1

                val tokens = line.split(" ")
                when (tokens[0]) {
                    "format" -> {
                        if (tokens[1] != "binary_little_endian") {
                            throw Exception("Unsupported ply format: ${tokens[1]}")
                        }
                    }

                    "element" -> {
                        iElement++
                        currentElement = tokens[1]
                        if (currentElement == "vertex") {
                            numVertices = tokens[2].toInt()
                        } else if (iElement == 1) {
                            throw Exception("vertex element expected (found: $currentElement)")
                        }
                    }

                    "property" -> {
                        if (currentElement == "vertex") {
                            val type = tokens[1]
                            val name = tokens[2]
                            logD { "Vertex property: $type $name" }
                            addParser(name, type)
                        }
                    }
                }
                line = fis.readHeaderLine()
            }
            headerSize += line.length + 1
            vertexSize = propertyParsers.sumBy { it.size }
        }
    }

    private fun addParser(propName: String, type: String) {
        propertyParsers += when (propName) {
            "x" -> floatParser(propName, type) { x = it }
            "y" -> floatParser(propName, type) { y = it }
            "z" -> floatParser(propName, type) { z = it }
            "red" -> ucharParser(propName, type) { color.r = (it.toInt() and 0xff).toFloat() / 255f }
            "green" -> ucharParser(propName, type) { color.r = (it.toInt() and 0xff).toFloat() / 255f }
            "blue" -> ucharParser(propName, type) { color.r = (it.toInt() and 0xff).toFloat() / 255f }
            else -> {
                when (type) {
                    "float" -> PropertyParser(4)
                    "int" -> PropertyParser(4)
                    "uchar" -> PropertyParser(1)
                    else -> throw Exception("Unknown type: $type (property: $propName)")
                }
            }
        }
    }

    private fun InputStream.readHeaderLine(): String {
        var c = read().toChar()
        val line = StringBuilder()
        while (c != '\n') {
            line.append(c)
            c = read().toChar()
        }
        return line.toString()
    }

    private fun requireType(type: String, actual: String, propName: String) {
        if (type != actual) {
            throw Exception("Expected type $type, found $actual (property: $propName")
        }
    }

    private fun floatParser(name: String, type: String, setter: Point.(Float) -> Unit): PropertyParser {
        requireType("float", type, name)
        return PropertyParser(4) { buf, pt -> pt.setter(buf.float) }
    }

    private fun ucharParser(name: String, type: String, setter: Point.(Byte) -> Unit): PropertyParser {
        requireType("uchar", type, name)
        return PropertyParser(1) { buf, pt -> pt.setter(buf.get()) }
    }

    private class PropertyParser(val size: Int, val parser: (ByteBuffer, Point) -> Unit = { _, _->})
}


private fun generateColor(points: List<Point>) {
    Log.d("PointCloud") { "Generating colors..." }
    val pointTree = pointTree(points)

    val trav = KNearestTraverser<Point>()
    val e0 = MutableVec3f()
    val e1 = MutableVec3f()
    val n0 = MutableVec3f()
    val n = MutableVec3f()

    points.forEachIndexed { index, pt ->
        trav.reset(pt, 50)
        pointTree.traverse(trav)

        n.set(Vec3f.ZERO)
        trav.result -= pt
        for (i in 1 until trav.result.size) {
            trav.result[i - 1].subtract(pt, e0).norm()
            trav.result[i].subtract(pt, e1).norm()
            e0.cross(e1, n0).norm()

            if (n0 * n < 0) {
                n0.scale(-1f)
            }
            n += n0
        }
        n.norm()
        pt.color.set(abs(n.x), abs(n.y), abs(n.z), 1f)

        if ((index+1) % 1_000_000 == 0) {
            Log.d("PlyReader") { "Generated ${(index+1)/1_000_000}M colors..." }
        }
    }

    Log.d("PointCloud") { "Done loading points" }
}
