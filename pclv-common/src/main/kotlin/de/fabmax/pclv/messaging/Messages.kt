package de.fabmax.pclv.messaging

import de.fabmax.kool.math.MutableVec3f
import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.util.serialization.MeshData
import kotlinx.serialization.Optional
import kotlinx.serialization.SerialId
import kotlinx.serialization.Serializable

@Serializable
data class Envelope(
        @SerialId(1) val name: String,

        @SerialId(2) @Optional val camRequest: CamRequest? = null,
        @SerialId(3) @Optional val meshData: MeshData? = null,
        @SerialId(4) @Optional val visibleNodes: VisibleNodes? = null
)

fun MeshData.asMessage() = Envelope("meshData", meshData = this)

@Serializable
data class VisibleNodes(
        @SerialId(1) val nodes: Set<String>
) { fun asMessage() = Envelope("visibleNodes", visibleNodes = this) }

@Serializable
data class CamRequest(
        @SerialId(1) val pos: SerVec3f,
        @SerialId(2) val lookAt: SerVec3f,
        @SerialId(3) val fovy: Float,
        @SerialId(4) val viewW: Int,
        @SerialId(5) val viewH: Int,
        @SerialId(6) val presentNodes: Set<String>
) { fun asMessage() = Envelope("camRequest", camRequest = this) }

@Serializable
data class SerVec3f(
        @SerialId(1) val x: Float,
        @SerialId(2) val y: Float,
        @SerialId(3) val z: Float
) {
    constructor(vec: Vec3f) : this(vec.x, vec.y, vec.z)
    fun toVec3f() = MutableVec3f(x, y, z)
}
