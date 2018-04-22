package de.fabmax.pclv.srv

import de.fabmax.kool.shading.AttributeType
import de.fabmax.kool.util.Log
import de.fabmax.kool.util.logD
import de.fabmax.kool.util.serialization.AttributeList
import de.fabmax.kool.util.serialization.MeshData
import de.fabmax.kool.util.serialization.PrimitiveType
import de.fabmax.pclv.messaging.CamRequest
import de.fabmax.pclv.messaging.VisibleNodes
import de.fabmax.pclv.pointTree.CameraQuery
import de.fabmax.pclv.pointTree.OcTree
import de.fabmax.pclv.pointTree.OcTreeNode
import de.fabmax.pclv.pointTree.Point
import fi.iki.elonen.NanoHTTPD
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.net.InetSocketAddress
import java.nio.ByteBuffer

class PclvServer(val pointCloud: OcTree<Point>, httpPort: Int, isDaemon: Boolean) {

    private val httpServer = HttpSrv(httpPort)
    private val wsServer = WebSocketSrv(InetSocketAddress("0.0.0.0", 8887))

    //private val clientVisualizer: ClientVisualizer? = if (visualizeClients) { ClientVisualizer() } else { null }
    private val clientVisualizer: ClientVisualizer? = null

    init {
        httpServer.start(NanoHTTPD.SOCKET_READ_TIMEOUT, isDaemon)
        wsServer.start()
    }

    fun close() {
        httpServer.closeAllConnections()
        wsServer.stop()
    }

    fun handleCamRequest(camRequest: CamRequest): Pair<VisibleNodes, List<MeshData>> {
        val clientCam = CameraQuery()
        camRequest.apply {
            clientCam.setup(pos.toVec3f(), lookAt.toVec3f(), viewW, viewH, fovy)
        }

        val nodes = pointCloud.collectNodesInFrustum(clientCam, 1000, 1_500_000)
        clientVisualizer?.setClient(camRequest, nodes)

        val visibleNodes = mutableSetOf<String>()
        visibleNodes += nodes.map { nd -> nd.nodeName }

        logD { "Got ${nodes.size} nodes containing ${nodes.sumBy { it.numPoints.toInt() }} points" }

        // remove all nodes the client already has
        nodes.removeAll { camRequest.presentNodes.contains(it.nodeName) }
        return Pair(VisibleNodes(visibleNodes), nodes.map { it.toMeshData() })
    }

    private fun OcTreeNode<Point>.toMeshData(): MeshData {
        val positions = mutableListOf<Float>()
        val colors = mutableListOf<Float>()

        for (pt in loadPoints()) {
            positions += pt.x
            positions += pt.y
            positions += pt.z

            colors += pt.color.r
            colors += pt.color.g
            colors += pt.color.b
            colors += pt.color.a
        }

        val attribs = mapOf(MeshData.ATTRIB_POSITIONS to AttributeList(AttributeType.VEC_3F, positions),
                MeshData.ATTRIB_COLORS to AttributeList(AttributeType.COLOR_4F, colors))
        return MeshData(nodeName, PrimitiveType.POINTS, attribs)
    }

    private class HttpSrv(private val port: Int = 8080) : NanoHTTPD(port) {
        override fun start() {
            super.start()
            Log.i(TAG) { "Waiting for HTTP connections on port $port..." }
        }

        override fun serve(session: IHTTPSession): Response {
            val method = session.method
            val uri = if (session.uri.endsWith("/")) { "${session.uri}index.html" } else { session.uri }
            val inStream = open(uri)

            logD { "$method ${session.uri}" }

            return if (inStream != null) {
                val mimeType = when(uri.split('.').last().toLowerCase()) {
                    "htm" -> "text/html"
                    "html" -> "text/html"
                    "js" -> "application/javascript"
                    "jpg" -> "image/jpg"
                    "png" -> "image/png"
                    else -> "application/octet-stream"
                }
                newFixedLengthResponse(Response.Status.OK, mimeType, inStream, inStream.available().toLong())
            } else {
                newFixedLengthResponse(Response.Status.NOT_FOUND,"text/html",
                        "<html><body><h1>Not Found: ${session.uri}</h1></body></html>")
            }
        }

        private fun open(uri: String): InputStream? {
            val inStream = this::class.java.classLoader.getResourceAsStream(RESOURCES_WEB_PATH + uri)
            return if (inStream != null) {
                inStream

            } else {
                val file = File(WEB_PATH + uri)
                if (file.exists()) {
                    FileInputStream(file)
                } else {
                    null
                }
            }
        }

        companion object {
            private const val WEB_PATH = "pclv-js/build/web/pclv-js"
            private const val RESOURCES_WEB_PATH = "pclv-js"
        }
    }

    private inner class WebSocketSrv(address: InetSocketAddress) : WebSocketServer(address) {
        private val connections = mutableMapOf<WebSocket, WebSocketClientConn>()

        override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
            logD { "new connection from ${conn.remoteSocketAddress}" }
            connections[conn] = WebSocketClientConn(this@PclvServer, conn)
        }

        override fun onClose(conn: WebSocket, code: Int, reason: String, remote: Boolean) {
            logD { "closed ${conn.remoteSocketAddress} with exit code $code additional info: $reason" }
            connections.remove(conn)
        }

        override fun onMessage(conn: WebSocket, message: String) {
            connections[conn]?.onMessage(message)
        }

        override fun onMessage(conn: WebSocket, message: ByteBuffer) {
            connections[conn]?.onMessage(message)
        }

        override fun onError(conn: WebSocket, ex: Exception) {
            logD { "an error occurred on connection ${conn.remoteSocketAddress}: $ex" }
        }

        override fun onStart() {
            Log.i(TAG) { "Waiting for WebSocket connections on $address..." }
        }
    }

    companion object {
        const val TAG = "pclv-srv"
    }
}