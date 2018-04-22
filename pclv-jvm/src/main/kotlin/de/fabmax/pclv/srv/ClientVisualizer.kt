package de.fabmax.pclv.srv

import de.fabmax.kool.createContext
import de.fabmax.kool.math.MutableVec3f
import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.scene.FrustumPlane
import de.fabmax.kool.scene.PerspectiveCamera
import de.fabmax.kool.scene.scene
import de.fabmax.kool.scene.sphericalInputTransform
import de.fabmax.kool.util.BoundingBox
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.LineMesh
import de.fabmax.kool.util.PointMesh
import de.fabmax.pclv.messaging.CamRequest
import de.fabmax.pclv.pointTree.CameraQuery
import de.fabmax.pclv.pointTree.OcTreeNode
import de.fabmax.pclv.pointTree.Point
import kotlin.concurrent.thread

class ClientVisualizer {

    private val lineMesh = LineMesh().apply { lineWidth = 2f }
    private val pointMesh = PointMesh().apply { pointSize = 3f }

    init {
        thread {
            val ctx = createContext()
            ctx.scenes += scene {
                +sphericalInputTransform {
                    verticalAxis = Vec3f.Z_AXIS
                    horizontalRotation = 90f
                    minHorizontalRot = 0f
                    maxHorizontalRot = 180f

                    maxZoom = 3000f
                    resetZoom(500f)
                    +camera

                    (camera as PerspectiveCamera).apply {
                        clipNear = 3f
                        clipFar = 3000f
                    }
                }

                +pointMesh
                +lineMesh
            }
            ctx.run()
        }
    }

    fun setClient(camReq: CamRequest, nodes: List<OcTreeNode<Point>>) {
        val clientCam = CameraQuery()
        camReq.apply {
            clientCam.setup(pos.toVec3f(), lookAt.toVec3f(), viewW, viewH, fovy)
        }

        lineMesh.meshData.batchUpdate {
            clear()

            val p0 = MutableVec3f(clientCam.position).add(Vec3f.X_AXIS)
            val p1 = MutableVec3f(clientCam.position).subtract(Vec3f.X_AXIS)
            lineMesh.addLine(p0, Color.MD_PINK, p1, Color.MD_PINK)

            p0.set(clientCam.position).add(Vec3f.Y_AXIS)
            p1.set(clientCam.position).subtract(Vec3f.Y_AXIS)
            lineMesh.addLine(p0, Color.MD_PINK, p1, Color.MD_PINK)

            p0.set(clientCam.position).add(Vec3f.Z_AXIS)
            p1.set(clientCam.position).subtract(Vec3f.Z_AXIS)
            lineMesh.addLine(p0, Color.MD_PINK, p1, Color.MD_PINK)

            val nearPlane = FrustumPlane()
            val farPlane = FrustumPlane()
            clientCam.computeFrustumPlane(0f, nearPlane)
            clientCam.computeFrustumPlane(1f, farPlane)

            lineMesh.addLine(nearPlane.upperLeft, Color.MD_LIGHT_GREEN, nearPlane.upperRight, Color.MD_LIGHT_GREEN)
            lineMesh.addLine(nearPlane.upperRight, Color.MD_LIGHT_GREEN, nearPlane.lowerRight, Color.MD_LIGHT_GREEN)
            lineMesh.addLine(nearPlane.lowerRight, Color.MD_LIGHT_GREEN, nearPlane.lowerLeft, Color.MD_LIGHT_GREEN)
            lineMesh.addLine(nearPlane.lowerLeft, Color.MD_LIGHT_GREEN, nearPlane.upperLeft, Color.MD_LIGHT_GREEN)

            lineMesh.addLine(farPlane.upperLeft, Color.MD_LIGHT_GREEN, farPlane.upperRight, Color.MD_LIGHT_GREEN)
            lineMesh.addLine(farPlane.upperRight, Color.MD_LIGHT_GREEN, farPlane.lowerRight, Color.MD_LIGHT_GREEN)
            lineMesh.addLine(farPlane.lowerRight, Color.MD_LIGHT_GREEN, farPlane.lowerLeft, Color.MD_LIGHT_GREEN)
            lineMesh.addLine(farPlane.lowerLeft, Color.MD_LIGHT_GREEN, farPlane.upperLeft, Color.MD_LIGHT_GREEN)

            lineMesh.addLine(nearPlane.upperLeft, Color.MD_LIGHT_GREEN, farPlane.upperLeft, Color.MD_LIGHT_GREEN)
            lineMesh.addLine(nearPlane.upperRight, Color.MD_LIGHT_GREEN, farPlane.upperRight, Color.MD_LIGHT_GREEN)
            lineMesh.addLine(nearPlane.lowerRight, Color.MD_LIGHT_GREEN, farPlane.lowerRight, Color.MD_LIGHT_GREEN)
            lineMesh.addLine(nearPlane.lowerLeft, Color.MD_LIGHT_GREEN, farPlane.lowerLeft, Color.MD_LIGHT_GREEN)

            pointMesh.meshData.batchUpdate {
                pointMesh.meshData.clear()

                val aabb = BoundingBox()
                nodes.forEach {
                    val c = BOX_COLORS[(it.depth) % BOX_COLORS.size]
                    aabb.set(it.bounds)//.expand(it.bounds.size.scale(-0.01f, MutableVec3f()))
                    lineMesh.addBoundingBox(aabb, c)
//                    it.loadPoints().forEach {
//                        pointMesh.addPoint {
//                            position.set(it)
//                            color.set(c)
//                        }
//                    }
                }
            }
        }
    }

    companion object {
        private val BOX_COLORS = listOf(Color.MD_YELLOW, Color.MD_AMBER, Color.MD_ORANGE, Color.MD_DEEP_ORANGE,
                Color.MD_RED, Color.MD_PINK, Color.MD_PURPLE, Color.MD_DEEP_PURPLE, Color.MD_INDIGO, Color.MD_BLUE,
                Color.MD_LIGHT_BLUE, Color.MD_CYAN, Color.MD_TEAL, Color.MD_GREEN, Color.MD_LIGHT_GREEN, Color.MD_LIME)
    }
}