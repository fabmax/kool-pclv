import de.fabmax.kool.util.logE
import de.fabmax.kool.util.logI
import de.fabmax.pclv.messaging.Envelope
import de.fabmax.pclv.messaging.MessageExchange
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Uint8Array
import org.khronos.webgl.get
import org.khronos.webgl.set
import org.w3c.dom.ARRAYBUFFER
import org.w3c.dom.BinaryType
import org.w3c.dom.MessageEvent
import org.w3c.dom.WebSocket

class WebSocketClient(url: String): MessageExchange() {

    private val socket = WebSocket(url)
    private var isOpened = false

    init {
        socket.binaryType = BinaryType.ARRAYBUFFER

        socket.onopen = {
            isOpened = true
            logI { "WebSocket connection opened" }
        }

        socket.onclose = {
            isOpened = false
            logI { "WebSocket connection closed" }
        }

        socket.onerror = {
            logE { "WebSocket encountered an error" }
        }

        socket.onmessage = { evt ->
            evt as MessageEvent
            when {
                evt.data is String -> received(evt.data as String)
                evt.data is ArrayBuffer -> received((evt.data as ArrayBuffer).toByteArray())
                else -> logE { "unknown data type ${evt.data}" }
            }
        }
    }

    override fun send(msg: Envelope) {
        if (isOpened) {
            super.send(msg)
        }
    }

    override fun send(msgData: String) = socket.send(msgData)

    override fun send(msgData: ByteArray) = socket.send(msgData.toArrayBuffer())

    private fun ByteArray.toArrayBuffer(): Uint8Array {
        val buf = Uint8Array(size)
        for (i in indices) {
            buf[i] = this[i]
        }
        return buf
    }

    private fun ArrayBuffer.toByteArray(): ByteArray {
        val array = Uint8Array(this)
        val bytes = ByteArray(array.length)
        for (i in 0 until array.length) {
            bytes[i] = array[i]
        }
        return bytes
    }

}