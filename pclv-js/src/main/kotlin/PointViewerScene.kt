import de.fabmax.kool.createContext
import de.fabmax.kool.math.MutableVec2f
import de.fabmax.kool.math.MutableVec3f
import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.scene.*
import de.fabmax.kool.util.debugOverlay
import de.fabmax.kool.util.logD
import de.fabmax.kool.util.pointMesh
import de.fabmax.kool.util.serialization.MeshData
import de.fabmax.pclv.messaging.CamRequest
import de.fabmax.pclv.messaging.SerVec3f
import de.fabmax.pclv.messaging.VisibleNodes
import kotlin.browser.window

fun main() {
    PointViewerScene()
}

class PointViewerScene {
    private val wsClient = WebSocketClient("ws://${window.location.hostname}:8887")
    private var lastRequestT = 0.0

    private val lastPos = MutableVec3f()
    private val lastVpSize = MutableVec2f()
    private val vpSize = MutableVec2f()

    private val pointScene: Scene
    private val pointGroup = PointGroup()

    init {
        val ctx = createContext()
        pointScene = scene {
            +sphericalInputTransform {
                verticalAxis = Vec3f.Z_AXIS
                horizontalRotation = 90f
                minHorizontalRot = 0f
                maxHorizontalRot = 180f

                +camera
                (camera as PerspectiveCamera).apply {
                    clipNear = 1f
                    clipFar = 1000f
                }

                maxZoom = 1000f
                resetZoom(200f)

                camera.onPreRender += { ctx ->
                    vpSize.set(ctx.viewport.width.toFloat(), ctx.viewport.height.toFloat())

                    if (ctx.time - lastRequestT > 1.0 && (camera.globalPos.distance(lastPos) > 0.1 || lastVpSize != vpSize)) {
                        lastRequestT = ctx.time
                        lastVpSize.set(vpSize)
                        lastPos.set(camera.globalPos)

                        val cam = camera as PerspectiveCamera
                        val pos = SerVec3f(camera.globalPos)
                        val lookAt = SerVec3f(camera.globalLookAt)
                        val vpW = ctx.viewport.width
                        val vpH = ctx.viewport.height
                        wsClient.send(CamRequest(pos, lookAt, cam.fovy, vpW, vpH, pointGroup.getNodeNames()).asMessage())
                    }

                }
            }
            +pointGroup
        }
        ctx.scenes += pointScene
        ctx.scenes += debugOverlay(ctx)
        ctx.run()

        wsClient.installHandler("meshData", { meshData!! }) { pointGroup.addPoints(it) }
        wsClient.installHandler("visibleNodes", { visibleNodes!! }) {pointGroup.setVisibleNodes(it) }
    }

    inner class PointGroup : Group() {
        private val visibleNodes = mutableSetOf<String>()
        private val presentNodes = mutableSetOf<String>()

        fun getNodeNames(): Set<String> {
            val names = mutableSetOf<String>()
            children.forEach {
                names += it.name!!
            }
            return names
        }

        fun addPoints(pointData: MeshData) {
            if (pointData.name in visibleNodes && pointData.name !in presentNodes) {
                val positions = pointData.attributes[MeshData.ATTRIB_POSITIONS]!!
                val colors = pointData.attributes[MeshData.ATTRIB_COLORS]
                val mesh = pointMesh(pointData.name) {
                    pointSize = 4f
                    for (i in 0 until pointData.numVertices) {
                        addPoint {
                            position.set(positions[i * 3], positions[i * 3 + 1], positions[i * 3 + 2])
                            if (colors != null) {
                                color.set(colors[i * 4], colors[i * 4 + 1], colors[i * 4 + 2], colors[i * 4 + 3])
                            } else {
                                color.set(0.5f, 0.5f, 0.5f, 1f)
                            }
                        }
                    }
                }
                this += mesh
                presentNodes += pointData.name
            }
        }

        fun setVisibleNodes(newVisibleNodes: VisibleNodes) {
            visibleNodes.clear()
            visibleNodes += newVisibleNodes.nodes

            val removeNodes = mutableSetOf<Node>()
            for (node in children) {
                if (node.name !in visibleNodes) {
                    removeNodes += node
                }
            }

            logD { "Visible nodes: c: ${children.size}, ar: ${children.size - removeNodes.size}, f: ${visibleNodes.size}" }
            removeNodes.forEach { node ->
                removeNode(node)
                pointScene.dispose(node)
                presentNodes -= node.name!!
            }
        }
    }
}