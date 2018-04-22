package de.fabmax.pclv.srv

import de.fabmax.pclv.messaging.MessageExchange
import de.fabmax.pclv.messaging.asMessage
import kotlinx.coroutines.experimental.*
import org.java_websocket.WebSocket
import java.nio.ByteBuffer

class WebSocketClientConn(private val pclvServer: PclvServer, private val conn: WebSocket) : MessageExchange() {

    private val clientSendCtx = newSingleThreadContext("clientSendCtx")
    private var clientSender: Job? = null

    init {
        installHandler("camRequest", { camRequest!! }) { camReq ->
            clientSender?.cancel()

            val (visibleNodes, nodes) = pclvServer.handleCamRequest(camReq)

            runBlocking {
                clientSender?.join()
            }
            clientSender = launch(clientSendCtx) {
                send(visibleNodes.asMessage())

                var i = 0
                for (node in nodes) {
                    if (!isActive) {
                        // co-routine was canceled (because of a new request
                        break
                    }
                    send(node.asMessage())
                    if (++i % 25 == 0) {
                        delay(50)
                    }
                }
            }

//            camReq.apply {
//                clientCam.setup(pos.toVec3f(), lookAt.toVec3f(), viewW, viewH, fovy)
//            }
//
//            clientSender?.cancel()
//
//            val nodes = pclvServer.pointCloud.collectNodesInFrustum(clientCam, 1000, 1_500_000)
//            pclvServer.clientVisualizer?.setClient(camReq, nodes)
//            runBlocking {
//                clientSender?.join()
//            }
//
//            logD { "Transmitting ${nodes.size} nodes containing ${nodes.sumBy { it.numPoints.toInt() }} points" }
//
//            clientSender = launch(clientSendCtx) {
//                val visibleNodes = mutableSetOf<String>()
//                visibleNodes += nodes.map { nd -> nd.nodeName }
//                send(VisibleNodes(visibleNodes).asMessage())
//
//                var i = 0
//                for (node in nodes) {
//                    if (!isActive) {
//                        break
//                    }
//                    if (!camReq.presentNodes.contains(node.nodeName)) {
//                        send(node.toMeshData().asMessage())
//                        if (++i % 25 == 0) {
//                            delay(50)
//                        }
//                    }
//                }
//            }
        }
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