package de.fabmax.pclv.srv

import de.fabmax.kool.shading.AttributeType
import de.fabmax.kool.util.logD
import de.fabmax.kool.util.serialization.AttributeList
import de.fabmax.kool.util.serialization.MeshData
import de.fabmax.kool.util.serialization.PrimitiveType
import de.fabmax.pclv.messaging.MessageExchange
import de.fabmax.pclv.messaging.VisibleNodes
import de.fabmax.pclv.messaging.asMessage
import de.fabmax.pclv.pointTree.CameraQuery
import de.fabmax.pclv.pointTree.OcTreeNode
import de.fabmax.pclv.pointTree.Point
import kotlinx.coroutines.experimental.*
import org.java_websocket.WebSocket
import java.nio.ByteBuffer

class WebSocketClientConn(private val pclvServer: PclvServer, private val conn: WebSocket) : MessageExchange() {

    private val clientCam = CameraQuery()
    private val clientSendCtx = newSingleThreadContext("clientSendCtx")
    private var clientSender: Job? = null

    init {
        installHandler("camRequest", { camRequest!! }) { camReq ->
            //println("Got camera request: $camReq")
            camReq.apply {
                clientCam.setup(pos.toVec3f(), lookAt.toVec3f(), viewW, viewH, fovy)
            }

            clientSender?.cancel()

            val nodes = pclvServer.pointCloud.collectNodesInFrustum(clientCam, 1000, 1_500_000)
            pclvServer.clientVisualizer?.setClient(camReq, nodes)
            runBlocking {
                clientSender?.join()
            }

            logD { "Transmitting ${nodes.size} nodes containing ${nodes.sumBy { it.numPoints.toInt() }} points" }

            clientSender = launch(clientSendCtx) {
                val visibleNodes = mutableSetOf<String>()
                visibleNodes += nodes.map { nd -> nd.nodeName }
                send(VisibleNodes(visibleNodes).asMessage())

                var i = 0
                for (node in nodes) {
                    if (!isActive) {
                        break
                    }
                    if (!camReq.presentNodes.contains(node.nodeName)) {
                        send(node.toMeshData().asMessage())
                        if (++i % 25 == 0) {
                            delay(50)
                        }
                    }
                }
            }
        }
    }

    private fun OcTreeNode<Point>.toMeshData(): MeshData {
        val positions = mutableListOf<Float>()
        val colors = mutableListOf<Float>()
        val attribs = mapOf(MeshData.ATTRIB_POSITIONS to AttributeList(AttributeType.VEC_3F, positions),
                MeshData.ATTRIB_COLORS to AttributeList(AttributeType.COLOR_4F, colors))

        for (pt in loadPoints()) {
            positions += pt.x
            positions += pt.y
            positions += pt.z

            colors += pt.color.r
            colors += pt.color.g
            colors += pt.color.b
            colors += pt.color.a
        }
        return MeshData(nodeName, PrimitiveType.POINTS, attribs)
    }

    override fun send(msgData: String) = conn.send(msgData)

    override fun send(msgData: ByteArray) = conn.send(msgData)

    fun onMessage(msgData: String) = received(msgData)

    fun onMessage(msgData: ByteBuffer) = received(msgData.toByteArray())

    private fun ByteBuffer.toByteArray(): ByteArray {
        val array = ByteArray(remaining())
        get(array)
        return array
    }
}