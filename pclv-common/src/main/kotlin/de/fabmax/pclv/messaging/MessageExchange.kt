package de.fabmax.pclv.messaging

import de.fabmax.kool.util.logW
import kotlinx.serialization.json.JSON
import kotlinx.serialization.protobuf.ProtoBuf

abstract class MessageExchange {
    private val receivers = mutableMapOf<String, MessageHandler<*>>()

    protected abstract fun send(msgData: String)

    protected abstract fun send(msgData: ByteArray)

    open fun send(msg: Envelope) = send(JSON.stringify(msg))

    open fun received(msgData: String) = received(JSON.parse<Envelope>(msgData))

    open fun received(msgData: ByteArray) = received(ProtoBuf.load<Envelope>(msgData))

    open fun received(msg: Envelope) = receivers[msg.name]?.consume(msg) ?: logW { "No handler for message: ${msg.name}" }

    open fun <T> installHandler(name: String, msgExtractor: Envelope.() -> T, msgConsumer: (T) -> Unit) {
        receivers[name] = MessageHandler(msgExtractor, msgConsumer)
    }

    protected data class MessageHandler<T>(
            private val msgExtractor: Envelope.() -> T,
            private val msgConsumer: (T) -> Unit
    ) {
        fun consume(msg: Envelope) = msgConsumer(msg.msgExtractor())
    }
}
